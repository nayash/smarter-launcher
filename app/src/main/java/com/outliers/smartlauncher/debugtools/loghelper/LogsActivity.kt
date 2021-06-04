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

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.debugtools.loghelper.FilesRVAdapter.FilesRVAdapterParent
import com.outliers.smartlauncher.utils.Utils
import java.io.File

class LogsActivity : AppCompatActivity(), FilesRVAdapterParent {
    var logHelper: LogHelper? = null
    var rvFiles: RecyclerView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        logHelper = LogHelper.getLogHelper(this)
        rvFiles = findViewById(R.id.rv_log_files)
        val adapter = FilesRVAdapter(logHelper?.allFilesInDir, this)
        rvFiles?.layoutManager = LinearLayoutManager(this)
        rvFiles?.adapter = adapter
    }

    override fun itemClicked(position: Int, path: String) {
        val file = File(path)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = FLAG_GRANT_READ_URI_PERMISSION
        val uri = FileProvider.getUriForFile(this, getString(R.string.smart_launcher_file_provider), file) //Uri.parse(file.absolutePath)
        intent.setDataAndType(uri, "text/plain")
        startActivity(intent)
        /*CoroutineScope(Dispatchers.IO).launch {
            val logStr = Utils.readFromFileAsString(this@LogsActivity, path)
            runOnUiThread {
                logStr?.let { Utils.showAlertDialog(this@LogsActivity, file.name, it, {}, {}) }
            }
        }*/
    }

    override fun share(position: Int, path: String?) {
        path?.let { shareLog(it) }
    }

    private fun shareLog(path: String) {
        LogHelper.getLogHelper(this)?.flush() // flush any queued logs before sharing logs
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("outliers91@gmail.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "User Logs")
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.issue_mail_template))
        val file = File(path)
        val uri: Uri = FileProvider.getUriForFile(
            applicationContext,
            getString(R.string.sl_file_provider),
            file
        )
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivityForResult(
            Intent.createChooser(
                intent,
                getString(R.string.choose_share_app)
            ), 1
        )
    }

    private fun shareLogs() {
        LogHelper.getLogHelper(this)?.flush() // flush any queued logs before sharing logs
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("outliers91@gmail.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "User Logs")
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.issue_mail_template))
        try {
            val sourceDir: File = Utils.getAppLogFolderInternal(this)
            val files: Array<File> = sourceDir.listFiles()
            val uris: ArrayList<Uri> = ArrayList()
            Log.e("getAllFilesInDir", "Size: " + files.size)
            intent.action = Intent.ACTION_SEND_MULTIPLE
            for (i in files.indices) {
                //Log.e("getAllFilesInDir", "FileName:" + files[i].getAbsolutePath());
                val uri: Uri = FileProvider.getUriForFile(
                    applicationContext,
                    getString(R.string.sl_file_provider),
                    files[i]
                )
                //Uri uri = Uri.parse("file://" + files[i]);
                //intent.putExtra(Intent.EXTRA_STREAM, uri);
                uris.add(uri)
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivityForResult(
                Intent.createChooser(
                    intent,
                    getString(R.string.choose_share_app)
                ), 1
            )
        } catch (e: Exception) {
            //Log.e("AttachError", "E", e);
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getContext(): Context {
        return this
    }
}