package com.outliers.smartlauncher.debugtools.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.debugtools.backup.FilesRVAdapter
import com.outliers.smartlauncher.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.security.Permission
import java.security.Permissions
import java.util.jar.Manifest

class BackupActivity : AppCompatActivity(), FilesRVAdapter.FilesRVAdapterParent {
    private val CREATE_FILE: Int = 1
    private lateinit var dataFiles: List<String>
    private val rootPath by lazy { Utils.getAppFolderInternal(this) }
    var rvFiles: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        dataFiles = rootPath.listFiles().map { it.absolutePath }
        rvFiles = findViewById(R.id.rv_log_files)
        val adapter = FilesRVAdapter(dataFiles.toList().toTypedArray(), this)
        rvFiles?.layoutManager = LinearLayoutManager(this)
        rvFiles?.adapter = adapter
    }

    override fun itemClicked(position: Int, path: String) {
        val file = File(path)
        CoroutineScope(Dispatchers.IO).launch {
            val logStr = Utils.readFromFileAsString(this@BackupActivity, path)
            runOnUiThread {
                logStr?.let { Utils.showAlertDialog(this@BackupActivity, file.name, it, {}, {}) }
            }
        }
    }

    override fun save(position: Int, path: String) {
        createFile(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).absolutePath, path
        )
    }

    override fun replace(position: Int, path: String) {

    }

    override fun getContext(): Context {
        return this
    }

    private fun createFile(backupPath: String?, path: String) {
        if (checkExternalWritePermission())
            CoroutineScope(Dispatchers.IO).launch {
                val data = Utils.readFromFileAsString(this@BackupActivity, path)
                Log.d("createFile", "test-download path= $backupPath")
                if (data != null && backupPath != null) {
                    val backupDir = File(backupPath, getString(R.string.app_name))
                    if(!backupDir.exists())
                        backupDir.mkdir()
                    Log.d("createFile", "test- backupDir path = ${backupDir.absolutePath}")
                    val temp = Utils.getFileNameFromPath(path)
                    val writePath =
                        backupDir.absolutePath + File.separator + temp
                    Log.d("createFile", "test-writePath = $writePath")
                    val res = Utils.writeToFile(
                        this@BackupActivity, writePath, data
                    )
                    runOnUiThread {
                        if (res)
                            Toast.makeText(
                                this@BackupActivity,
                                "File copied to $backupPath", Toast.LENGTH_SHORT
                            ).show()
                        else
                            Toast.makeText(
                                this@BackupActivity,
                                "File copy failed", Toast.LENGTH_SHORT
                            ).show()
                    }

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
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT)
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
}