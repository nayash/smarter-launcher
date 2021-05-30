/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.outliers.smartlauncher.debugtools.loghelper

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.StatFs
import android.util.Log
import com.outliers.smartlauncher.BuildConfig
import com.outliers.smartlauncher.consts.Constants.Companion.PREF_NAME
import com.outliers.smartlauncher.utils.Utils.getAppLogFolderInternal
import com.outliers.smartlauncher.utils.Utils.getDate
import com.outliers.smartlauncher.utils.Utils.getDeviceDetails
import com.outliers.smartlauncher.utils.Utils.getLibVersion
import java.io.*
import java.text.Format
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LogHelper private constructor(private val context: Context) {
    private val LOG_WRITE_BATCH_SIZE = if (BuildConfig.DEBUG) 1 else 10
    private val PAST_DAYS_LOGS_TO_KEEP = 4

    enum class LOG_LEVEL {
        INFO, WARNING, ERROR, CRITICAL
    }

    val logFile: File
    private var llPendingLogs: LinkedList<String>? = null
    private val executorService: ExecutorService = Executors.newFixedThreadPool(5)
    private var writeLogAsyncRunnable: Runnable? = null
    private var isLoggingAllowed = true
    private var lock: Any? = null
    private var logDateTimeFormat: Format? = null
    private var dummyDateObj: Date? = null
    private var shutdownHandler: Handler? = null
    private var shutdownRunnable: Runnable? = null
    var application: Application = context.applicationContext as Application
    private var logSP: SharedPreferences? = null

    private fun init() {
        lock = logFile
        llPendingLogs = LinkedList()
        writeLogAsyncRunnable = Runnable { writeLogs() }
        logDateTimeFormat = SimpleDateFormat(LOG_INFO_PREFIX_DATE_FORMAT)
        dummyDateObj = Date()
        shutdownRunnable = Runnable {
            executorService.shutdown()
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {

            }
            logHelper = null
        }
        shutdownHandler = Handler()
        flushPreviousSessionLogs()
        writeDeviceDetails()
        flush()
    }

    private fun writeDeviceDetails() {
        try {
            addLogToQueue(
                "App version : " + getLibVersion() +
                        ", Device details : " + getDeviceDetails(), LOG_LEVEL.INFO, "LogHelper"
            )
        } catch (ex: Exception) {
            Log.d("logging-exp", ex.message!!)
        }
    }

    val logSharedPref: SharedPreferences?
        get() {
            if (logSP == null) logSP =
                application.getSharedPreferences("LogHelper", Context.MODE_PRIVATE)
            return logSP
        }

    private fun flushPreviousSessionLogs() {
        val pendingLogs = logSharedPref!!.getString(SHARED_PREF_KEY_PENDING_LOG, "")
        if (!pendingLogs!!.isEmpty()) llPendingLogs!!.addAll(
            Arrays.asList(
                *pendingLogs.split("\n").toTypedArray()
            )
        )
        writePendingLogs()
        val pref = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
        val crashRestart = pref.getBoolean("crash_restart", false)
        Log.v("flushPrevLog", "$crashRestart, $pendingLogs")
        pref.edit().putString(SHARED_PREF_KEY_PENDING_LOG, "").commit()
        if (crashRestart) {
            pref.edit().putBoolean("crash_restart", false).apply()
            pref.edit().putString("crash_id", "").apply()
        }
    }

    fun addLogToQueue(logText: String?, level: LOG_LEVEL?, callingComponentName: String?) {
        var callingComponentName = callingComponentName
        if (logText == null || level == null) {
            return
        }
        if (callingComponentName == null) callingComponentName = ""
        addLogToQueue(formatLogText(logText, level, callingComponentName))
    }

    fun addLogToQueue(logText: String?, level: LOG_LEVEL?, callingComponent: Context?) {
        if (logText == null || level == null || callingComponent == null) {
            return
        }
        addLogToQueue(logText, level, callingComponent.javaClass.simpleName)
    }

    @Synchronized
    private fun addLogToQueue(logText: String) {
        if (BuildConfig.DEBUG) Log.e("test-addLogToQueueMain", logText)
        if (!isLoggingAllowed) return
        llPendingLogs!!.addLast(logText)
        if (llPendingLogs!!.size >= LOG_WRITE_BATCH_SIZE) {
            /** if collection size exceeds max allowed queue size,
             * call write method and dump written texts  */
            writePendingLogs()
        }
    }

    @Synchronized
    private fun writePendingLogs() {
        executorService.submit(writeLogAsyncRunnable)
    }

    fun disableLogging() {
        writePendingLogs()
        isLoggingAllowed = false
    }

    fun enableLogging() {
        isLoggingAllowed = true
    }

    private fun writeLogs() {
        /** Never call directly. Call writePendingLogs instead which executes this in separate thread  */
        synchronized(lock!!) {
            var s: String
            val sb = StringBuilder()
            while (llPendingLogs!!.poll().also { s = it } != null) {
                sb.append(
                    s.trimIndent()
                )
            }
            sb.deleteCharAt(sb.length - 1)
            writeLogs(logFile, sb.toString())
        }
    }

    val allFilesInDir: Array<String?>
        get() {
            val directory = File(logFile.parent)
            val files = directory.listFiles()
            val paths = arrayOfNulls<String>(files.size)
            Log.d("getAllFilesInDir", "Size: " + files.size)
            for (i in files.indices) {
                Log.d("getAllFilesInDir", "FileName:" + files[i].name)
                paths[i] = files[i].absolutePath
            }
            return paths
        }

    fun printLogFileContent() {
        try {
            BufferedReader(FileReader(logFile.absolutePath)).use { br ->
                val sb = StringBuilder()
                var line = br.readLine()
                while (line != null) {
                    sb.append(line)
                    sb.append("\n")
                    line = br.readLine()
                }
            }
        } catch (ex: Exception) {
            //Log.e("Error","printFileContent",ex);
        }
    }

    fun getFileContent(filePath: String?): String {
        val sb = StringBuilder()
        try {
            BufferedReader(FileReader(filePath)).use { br ->
                var line = br.readLine()
                while (line != null) {
                    sb.append(line)
                    sb.append("\n")
                    line = br.readLine()
                }
            }
        } catch (ex: Exception) {
            Log.e("Error", "printFileContent", ex)
        }
        return sb.toString()
    }

    fun deleteLogFile_NameLike(fileNameLike: String?) {
        val directory = File(logFile.parent)
        val files = directory.listFiles()
        //Log.e("deleteLogFile_NameLike", "Size: "+ files.length);
        for (file in files) {
            //Log.e("deleteLogFile_NameLike", "FileName:" + files[i].getName());
            if (file.name.contains(fileNameLike!!)) {
                file.delete()
            }
        }
    }

    fun deleteLogFile_NameNotLike(fileNameLike: String?) {
        val directory = File(logFile.parent)
        val files = directory.listFiles()
        //Log.e("deleteLog_NameNotLike", "Size: "+ files.length);
        for (file in files) {
            //Log.e("deleteLog_NameNotLike", "FileName:" + files[i].getName());
            if (!file.name.contains(fileNameLike!!)) {
                file.delete()
            }
        }
    }

    fun deleteLogFile_NameNotIn(fileNameLike: Array<String?>) {
        val directory = File(logFile.parent)
        val files = directory.listFiles()
        val namesToKeep = Arrays.asList(*fileNameLike)
        var count = 0
        for (i in files.indices) {
            if (!namesToKeep.contains(getLogFileDate(files[i].name))) {
                //Log.e("deleteLogFile","deleting "+files[i].getName());
                files[i].delete()
                count++
            }
        }
        //Log.e("deleteLogFile",count+" files deleted");
    }

    fun getLogFileDate(fileName: String): String {
        return fileName.replace(LOG_FILE_PREFIX, "").replace(".txt", "")
    }

    fun deleteAllLogFile() {
        val directory = File(logFile.parent)
        val files = directory.listFiles()
        //Log.e("deleteAllLogFile", "Size: "+ files.length);
        for (i in files.indices) {
            //Log.e("deleteAllLogFile", "FileName:" + files[i].getName());
            files[i].delete()
        }
    }

    fun deleteOldLogs(pastDaysToKeep: Int) {
        var pastDaysToKeep = pastDaysToKeep
        if (pastDaysToKeep == -1) pastDaysToKeep = PAST_DAYS_LOGS_TO_KEEP
        val calendar = Calendar.getInstance()
        val temp = Calendar.getInstance()
        val daysToKeep = arrayOfNulls<String>(pastDaysToKeep)
        for (i in 0 until pastDaysToKeep) {
            temp[Calendar.DAY_OF_MONTH] = calendar[Calendar.DAY_OF_MONTH] - i
            daysToKeep[i] = getDate(temp.timeInMillis, LOG_FILE_NAME_FORMAT)
        }
        deleteLogFile_NameNotIn(daysToKeep)
    }

    private fun convertToLogTime(time: Long): String {
        return try {
            dummyDateObj!!.time = time
            logDateTimeFormat!!.format(dummyDateObj)
        } catch (ex: Exception) {
            ""
        }
    }

    private fun formatLogText(logText: String, logLevel: LOG_LEVEL, componentName: String): String {
        return convertToLogTime(System.currentTimeMillis()) + "/" + componentName + "/" + logLevel.name[0] + " " + logText
    }

    private fun writeLogs(logFile: File?, logText: String?) {
        try {
            if (logFile == null || !logFile.exists() || logText == null || logText.isEmpty()) {
                return
            }
            synchronized(lock!!) {
                var buf: BufferedWriter? = null
                val fos: FileOutputStream? = null
                if (megabytesAvailable(logFile) > 5) {
                    try {
                        buf = BufferedWriter(
                            FileWriter(
                                logFile,
                                true
                            )
                        )
                        buf.append(logText)
                        buf.append("\r\n")
                        //buf.newLine();
                    } catch (e: Exception) {
                        Log.e("test-writeLogEx1", Log.getStackTraceString(e))
                    } finally {
                        buf!!.flush()
                        buf.close()
                    }
                } else {
                    // TODO handle low memory
                }
            }
        } catch (ex: Exception) {
            Log.e("test-writeLogEx2", Log.getStackTraceString(ex))
        }
    }

    fun logFWFinish() {
        /** do all resource clean up here  */
        //Log.e("LogFWFinish","Called");
        flush()
        //TODO use handler to wait for 3 seconds before shutting down. If getInstance called again cancel the handler.
        shutdownHandler!!.postDelayed(shutdownRunnable!!, 5000)
        //logHelper = null;
    }

    fun flush() {
        if (llPendingLogs!!.size > 0) writePendingLogs()
    }

    fun flushToSP() {
        val sb = StringBuilder()
        while (llPendingLogs!!.size > 0) {
            sb.append(llPendingLogs!!.poll())
            sb.append("\n")
        }
        logSharedPref!!.edit().putString(SHARED_PREF_KEY_PENDING_LOG, sb.toString()).commit()
    }

    fun pause() {
        flush()
        shutdownHandler!!.postDelayed(shutdownRunnable!!, 5000)
    }

    fun resume() {
        //flushPreviousSessionLogs();
        if (shutdownHandler != null) shutdownHandler!!.removeCallbacks(shutdownRunnable!!)
    }

    fun getTempDir(context: Context): String {
        return context.cacheDir.toString() + "/.logtemp"
    }

    fun handleCrash(context: Context, exception: Throwable?, crashId: String) {
        getLogHelper(context)!!.flushToSP()
        val pendingLogs = getLogHelper(application)!!.logSharedPref!!.getString(
            SHARED_PREF_KEY_PENDING_LOG, ""
        )
        getLogHelper(application)!!.logSharedPref!!.edit().putString(
            SHARED_PREF_KEY_PENDING_LOG, "${pendingLogs}crashId->$crashId:".trimIndent() +
                    formatLogText(
                        Log.getStackTraceString(exception),
                        LOG_LEVEL.CRITICAL, context.javaClass.simpleName
                    )
        ).commit()
    }

    companion object {
        const val LOG_FILE_NAME_FORMAT = "dd_MM_yyyy" //-HH_mm_ss
        const val LOG_INFO_PREFIX_DATE_FORMAT = "HH:mm:ss" //dd MM yyyy
        const val LOG_FILE_PREFIX = "SmartLauncherLog_"
        const val SHARED_PREF_KEY_PENDING_LOG = "pending_logs"
        private var logHelper: LogHelper? = null
        fun getLogHelper(context: Context): LogHelper? {
            if (logHelper == null) {
                Log.e("test-logHelper", "constructor called")
                logHelper = LogHelper(context)
            }
            return logHelper
        }

        fun megabytesAvailable(f: File): Float {
            val stat = StatFs(f.path)
            var bytesAvailable: Long = 0
            bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            return bytesAvailable / (1024f * 1024f)
        }
    }

    init {
        logFile = File(
            getAppLogFolderInternal(
                context
            ), LOG_FILE_PREFIX +
                    getDate(System.currentTimeMillis(), LOG_FILE_NAME_FORMAT) + ".txt"
        )
        Log.d("test-LogHelper", logFile.absolutePath)
        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
                deleteOldLogs(PAST_DAYS_LOGS_TO_KEEP)
            } catch (e: IOException) {
                //e.printStackTrace();
            }
        }
        init()
    }
}