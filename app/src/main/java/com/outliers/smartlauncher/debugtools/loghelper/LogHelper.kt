/*
 *     Copyright (C) 2021.  Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.outliers.smartlauncher.debugtools.loghelper

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.StatFs
import android.util.Log
import com.outliers.smartlauncher.BuildConfig
import com.outliers.smartlauncher.core.SmartLauncherApplication
import com.outliers.smartlauncher.utils.Utils.getAppLogFolderInternal
import com.outliers.smartlauncher.utils.Utils.getDate
import com.outliers.smartlauncher.utils.Utils.getDeviceDetails
import com.outliers.smartlauncher.utils.Utils.getLibVersion
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.text.Format
import java.text.SimpleDateFormat
import java.util.*

class LogHelper private constructor(private val context: Context) {
    private val PAST_DAYS_LOGS_TO_KEEP = 4

    enum class LOG_LEVEL {
        INFO, WARNING, ERROR, CRITICAL
    }

    val logFile: File
    private var isLoggingAllowed = true
    private var lock: Any? = null
    private var logDateTimeFormat: Format? = null
    private var dummyDateObj: Date? = null
    var application: Application = context.applicationContext as Application
    private var logSP: SharedPreferences? = null

    companion object {
        const val LOG_FILE_NAME_FORMAT = "dd_MM_yyyy"
        const val LOG_INFO_PREFIX_DATE_FORMAT = "HH:mm:ss"
        const val LOG_FILE_PREFIX = "SmartLauncherLog_"
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

    private fun init() {
        lock = logFile
        logDateTimeFormat = SimpleDateFormat(LOG_INFO_PREFIX_DATE_FORMAT)
        dummyDateObj = Date()
        writeDeviceDetails()
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

    fun addLogToQueue(logText: String?, level: LOG_LEVEL?, callingComponentName: String?) {
        var callingComponentName = callingComponentName
        if (logText == null || level == null) {
            return
        }
        if (callingComponentName == null) callingComponentName = ""
        addLogToQueue(formatLogText(logText, level, callingComponentName), level)
    }

    fun addLogToQueue(logText: String?, level: LOG_LEVEL?, callingComponent: Context?) {
        if (logText == null || level == null || callingComponent == null) {
            return
        }
        addLogToQueue(logText, level, callingComponent.javaClass.simpleName)
    }

    private fun addLogToQueue(logText: String, level: LOG_LEVEL) {
        if (!isLoggingAllowed) return
        val tag = "test-smartlauncher"
        when (level) {
            LOG_LEVEL.INFO -> Log.i(tag, logText)
            LOG_LEVEL.ERROR -> Log.e(tag, logText)
            LOG_LEVEL.CRITICAL -> Log.e(tag, logText)
            LOG_LEVEL.WARNING -> Log.w(tag, logText)
        }
    }

    fun disableLogging() {
        isLoggingAllowed = false
    }

    fun enableLogging() {
        isLoggingAllowed = true
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

    fun deleteLogFile_NameLike(fileNameLike: String) {
        val directory = File(logFile.parent)
        val files = directory.listFiles()
        //Log.e("deleteLogFile_NameLike", "Size: "+ files.length);
        for (file in files) {
            //Log.e("deleteLogFile_NameLike", "FileName:" + files[i].getName());
            if (file.name.contains(fileNameLike)) {
                file.delete()
            }
        }
    }

    fun deleteLogFile_NameNotLike(fileNameLike: String) {
        val directory = File(logFile.parent)
        val files = directory.listFiles()
        //Log.e("deleteLog_NameNotLike", "Size: "+ files.length);
        for (file in files) {
            //Log.e("deleteLog_NameNotLike", "FileName:" + files[i].getName());
            if (!file.name.contains(fileNameLike)) {
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

    fun flush() {
        SmartLauncherApplication.instance.flushLogs()
    }
}