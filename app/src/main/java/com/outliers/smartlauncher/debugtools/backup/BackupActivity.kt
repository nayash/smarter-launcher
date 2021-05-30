/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.debugtools.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.databinding.ActivityBackupBinding
import com.outliers.smartlauncher.debugtools.loghelper.LogHelper
import com.outliers.smartlauncher.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.*


class BackupActivity : AppCompatActivity(), FilesRVAdapter.FilesRVAdapterParent {
    private val PICK_FILE_REQUEST_CODE: Int = 2
    private val PICK_FILES_REQUEST_CODE: Int = 3
    private val CREATE_FILE: Int = 1
    private lateinit var dataFiles: MutableList<String>
    private val dataFilesLiveData = MutableLiveData<MutableList<String>>()
    private val rootPath by lazy { Utils.getAppDataFolderInternal(this) }
    val binding by lazy { ActivityBackupBinding.inflate(layoutInflater) }
    var rvFiles: RecyclerView? = null
    var replaceFilePath: String? = null
    val backupPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        dataFiles = rootPath.listFiles().map { it.absolutePath }
            .filter { !it.contains(LogHelper.LOG_FILE_PREFIX) }.toMutableList()
        dataFilesLiveData.value = dataFiles
        rvFiles = binding.rvLogFiles
        val adapter = FilesRVAdapter(dataFiles as java.util.ArrayList<String>?, this)
        rvFiles?.layoutManager = LinearLayoutManager(this)
        rvFiles?.adapter = adapter

        dataFilesLiveData.observe(this, {
            if (it.isEmpty()) {
                binding.tvRestore.visibility = View.VISIBLE
                rvFiles?.visibility = View.GONE
                binding.tvRestore.setOnClickListener {
                    restoreAllData()
                }
            } else {
                binding.tvRestore.visibility = View.GONE
                rvFiles?.visibility = View.VISIBLE
            }
            Log.d("test-observe", it.size.toString())
            rvFiles?.adapter?.notifyDataSetChanged()
        })
    }

    fun restoreAllData() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, PICK_FILES_REQUEST_CODE)
    }

    override fun itemClicked(position: Int, path: String) {
        val file = File(path)
        /*CoroutineScope(Dispatchers.IO).launch {
            val logStr = Utils.readFromFileAsString(this@BackupActivity, path)
            runOnUiThread {
                logStr?.let { Utils.showAlertDialog(this@BackupActivity, file.name, it, {}, {}) }
            }
        }*/
        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val uri = FileProvider.getUriForFile(this, getString(R.string.smart_launcher_file_provider), file) //Uri.parse(file.absolutePath)
        intent.setDataAndType(uri, "text/plain")
        startActivity(intent)
    }

    override fun save(position: Int, path: String) {
        createFile(
            backupPath.absolutePath, path
        )
    }

    override fun replace(position: Int, path: String) {
        replaceFilePath = path
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    fun replaceFile(source: Uri, dest: String) {
        Log.d("replaceFile", "test-$source, $dest")
        CoroutineScope(Dispatchers.IO).launch {
            val data = Utils.readFromFileAsString(this@BackupActivity, source)
            data?.let {
                val res = Utils.writeToFile(this@BackupActivity, dest, it)
                runOnUiThread {
                    if (res) Toast.makeText(
                        this@BackupActivity,
                        "File write done", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun getContext(): Context {
        return this
    }

    private fun createFile(backupPath: String?, path: String) {
        if (checkExternalWritePermission()) {
            try {
                FileUtils.copyFileToDirectory(
                    File(path),
                    File(backupPath, getString(R.string.app_name))
                )
                Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
            } catch (ex: IOException) {
                LogHelper.getLogHelper(this)
                    ?.addLogToQueue(
                        "test-backupException--${Log.getStackTraceString(ex)}",
                        LogHelper.LOG_LEVEL.ERROR,
                        this
                    )
                FirebaseCrashlytics.getInstance().recordException(ex)
                Toast.makeText(context, getText(R.string.all_files_permission), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun checkExternalWritePermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        requestPermissions(
            this,
            arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
        )
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if ((ContextCompat.checkSelfPermission(
                            this@BackupActivity,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED)
                    ) {
                        Toast.makeText(
                            this,
                            "Permission Granted. Retry the operation now.",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                } else {
                    Toast.makeText(
                        this, getString(R.string.write_denied_consequence),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    val fileName = getFileName(uri)
                    val file = File(rootPath, fileName)
                    copyToFile(uri, file)
                    Log.d("onActivityRes", "test-copied to ${file.absolutePath}")
                    rvFiles?.adapter?.notifyDataSetChanged()
                    sendBroadcast(Intent(Constants.ACTION_LAUNCHER_DATA_REFRESH))
                }
            }
        } else if (requestCode == PICK_FILES_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                data?.let {
                    val tempList = ArrayList<String>()
                    val count = it.clipData?.itemCount ?: 0
                    for (i in 0 until count) {
                        val uri = it.clipData?.getItemAt(i)?.uri
                        uri?.let {
                            val fileName = getFileName(uri)
                            val file = File(rootPath, fileName)
                            copyToFile(uri, file)
                            tempList.add(file.absolutePath)
                            Log.d("onActivityRes", "test-copyAll ${file.absolutePath}")
                        }
                    }
                    dataFiles.addAll(tempList)
                    dataFilesLiveData.value = dataFiles
                    rvFiles?.adapter?.notifyDataSetChanged()
                    sendBroadcast(Intent(Constants.ACTION_LAUNCHER_DATA_REFRESH))
                }
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    private fun getFileName(uri: Uri): String? {
        // Obtain a cursor with information regarding this uri
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor!!.count <= 0) {
            cursor.close()
            throw IllegalArgumentException("Can't obtain file name, cursor is empty")
        }
        cursor.moveToFirst()
        val fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        cursor.close()
        return fileName
    }

    @Throws(IOException::class)
    private fun copyToFile(uri: Uri, file: File) {
        // Obtain an input stream from the uri
        /*val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to obtain input stream from URI")
        // Copy the stream to the temp file
        copyInputStreamToFile(inputStream, file)*/
        try {
            val `in` = contentResolver.openInputStream(uri)
            val r = BufferedReader(InputStreamReader(`in`))
            val total = StringBuilder()
            var line: String?
            while (r.readLine().also { line = it } != null) {
                total.append(line).append('\n')
            }
            val content = total.toString()
            FileUtils.writeStringToFile(file, content)
        } catch (e: Exception) {
            Log.e("test-copyToFile", Log.getStackTraceString(e))
        }
    }

    @Throws(IOException::class)
    private fun copyInputStreamToFile(inputStream: InputStream, file: File) {
        // append = false
        FileOutputStream(file, false).use { outputStream ->
            var read: Int
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (inputStream.read(bytes).also { read = it } != -1) {
                outputStream.write(bytes, 0, read)
            }
        }
    }
}