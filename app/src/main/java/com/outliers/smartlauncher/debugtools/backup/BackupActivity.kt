package com.outliers.smartlauncher.debugtools.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.io.*


class BackupActivity : AppCompatActivity(), FilesRVAdapter.FilesRVAdapterParent {
    private val PICK_FILE_REQUEST_CODE: Int = 2
    private val CREATE_FILE: Int = 1
    private lateinit var dataFiles: List<String>
    private val rootPath by lazy { Utils.getAppFolderInternal(this) }
    var rvFiles: RecyclerView? = null
    var replaceFilePath: String? = null

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
                runOnUiThread{
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
            FileUtils.copyFileToDirectory(
                File(path),
                File(backupPath, getString(R.string.app_name))
            )
            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
        }
            /*CoroutineScope(Dispatchers.IO).launch {
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

                }*/
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == PICK_FILE_REQUEST_CODE){
            if(resultCode == RESULT_OK){
                val uri = data?.data
                if(uri != null){
                    val fileName = getFileName(uri)
                    val file = File(Utils.getAppFolderInternal(this), fileName)
                    copyToFile(uri, file)
                    Log.d("onActivityRes", "test-copied to ${file.absolutePath}")
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