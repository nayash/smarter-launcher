package com.outliers.smartlauncher.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.MutableLiveData


class SmartLauncherApplication: Application() {

    var smartLauncherRoot: SmartLauncherRoot? = null
    val appListRefreshed: MutableLiveData<Boolean> = MutableLiveData()

    override fun onCreate() {
        super.onCreate()

        val intentFilterInstalled = IntentFilter()
        intentFilterInstalled.addAction(Intent.ACTION_PACKAGE_ADDED)
        intentFilterInstalled.addAction(Intent.ACTION_PACKAGE_INSTALL)
        intentFilterInstalled.addDataScheme("package")
        registerReceiver(appInstallBR, intentFilterInstalled)

        val intentFilterUninstalled = IntentFilter()
        intentFilterUninstalled.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilterUninstalled.addDataScheme("package")
        registerReceiver(appUninstallBR, intentFilterUninstalled)

        smartLauncherRoot = SmartLauncherRoot.getInstance(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when(level){
            TRIM_MEMORY_RUNNING_LOW -> cleanAndBackUp()
        }
    }

    fun cleanAndBackUp(){
        // TODO resource/cache cleanup, if any
    }

    private val appInstallBR = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            // TODO handle new app installed
            val packageName = intent?.data?.encodedSchemeSpecificPart
            Log.v("test", "app installed:$packageName")
            smartLauncherRoot?.refreshAppList()
            appListRefreshed.value = true
        }
    }

    private val appUninstallBR = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            // TODO handle app uninstalled
            val packageName = intent?.data?.encodedSchemeSpecificPart
            Log.v("test", "app uninstalled:$packageName")
            smartLauncherRoot?.refreshAppList()
            appListRefreshed.value = true
        }
    }
}