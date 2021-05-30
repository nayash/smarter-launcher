/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

    override fun onCreate() {
        super.onCreate()

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

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val crashId: String =
                UUID.randomUUID().toString()
            smartLauncherRoot?.launcherPref?.edit()?.putBoolean("crash_restart", true)?.commit()
            smartLauncherRoot?.launcherPref?.edit()?.putString("crash_id", crashId)?.commit()
            LogHelper.getLogHelper(this)?.handleCrash(this, exception, crashId)
            val test: Boolean =
                smartLauncherRoot?.launcherPref?.getBoolean(
                    "crash_restart",
                    false
                ) == true
            Log.e("test", test.toString() + "")
            defaultHandler.uncaughtException(
                thread,
                exception
            )
        }
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
        try {
            val pid = android.os.Process.myPid()
            val cmd = "logcat --pid=$pid -d -f ${LogHelper.getLogHelper(this)?.logFile?.absolutePath}"
            Runtime.getRuntime().exec(cmd)
        }catch (ex:Exception){
            Log.e("test-cleanAndBackUp","failed to write logs: ${Log.getStackTraceString(ex)}")
        }
        smartLauncherRoot?.cleanUp()
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