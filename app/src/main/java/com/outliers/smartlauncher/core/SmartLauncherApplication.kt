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

package com.outliers.smartlauncher.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.outliers.smartlauncher.debugtools.loghelper.LogHelper
import java.lang.Exception
import java.util.*


class SmartLauncherApplication : Application() {

    var smartLauncherRoot: SmartLauncherRoot? = null
    val appListRefreshed: MutableLiveData<Boolean> = MutableLiveData()

    companion object {
        lateinit var instance: SmartLauncherApplication
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
        val intentFilterInstalled = IntentFilter()
        intentFilterInstalled.addAction(Intent.ACTION_PACKAGE_ADDED)
        intentFilterInstalled.addDataScheme("package")
        registerReceiver(appInstallBR, intentFilterInstalled)

        val intentFilterUninstalled = IntentFilter()
        intentFilterUninstalled.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilterUninstalled.addDataScheme("package")
        registerReceiver(appUninstallBR, intentFilterUninstalled)

        Log.d("test-slApplication", "calling root")
        smartLauncherRoot = SmartLauncherRoot.getInstance(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when (level) {
            TRIM_MEMORY_RUNNING_LOW -> cleanAndBackUp()
            TRIM_MEMORY_BACKGROUND -> {
                cleanAndBackUp()
                Log.v("test-application", "BG called")
            }
            TRIM_MEMORY_UI_HIDDEN -> {
                cleanAndBackUp()
                Log.v("test-application", "UI Hidden called")
            }
        }
    }

    fun cleanAndBackUp() {
        flushLogs()
        smartLauncherRoot?.cleanUp()
    }

    fun flushLogs() {
        try {
            val pid = android.os.Process.myPid()
            val cmd = "logcat --pid=$pid -d -f ${LogHelper.getLogHelper(this)?.logFile?.absolutePath}"
            Runtime.getRuntime().exec(cmd)
        }catch (ex:Exception){
            Log.e("test-cleanAndBackUp","failed to write logs: ${Log.getStackTraceString(ex)}")
        }
    }

    private val appInstallBR = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val packageName = intent?.data?.encodedSchemeSpecificPart
            Log.v("test-", "app installed:$packageName")
            smartLauncherRoot?.refreshAppList(1, packageName)
            appListRefreshed.value = true
        }
    }

    private val appUninstallBR = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val packageName = intent?.data?.encodedSchemeSpecificPart
            Log.v("test-", "app uninstalled:$packageName")
            smartLauncherRoot?.refreshAppList(0, packageName)
            appListRefreshed.value = true
        }
    }
}