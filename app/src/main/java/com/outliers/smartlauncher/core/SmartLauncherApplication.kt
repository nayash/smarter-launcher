package com.outliers.smartlauncher.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.outliers.smartlauncher.utils.LogHelper
import java.util.*


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

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // if crash involves any component of HLIB Library, log it; you could remove this check and log all crashes too and use this for your own debugging as well
                // Save the fact we crashed out.
                val crashId: String =
                    UUID.randomUUID().toString() // optional -- can be set to any value
                smartLauncherRoot?.launcherPref?.edit()?.putBoolean("crash_restart", true)?.commit()
                smartLauncherRoot?.launcherPref?.edit()?.putString("crash_id", crashId)?.commit()
                LogHelper.getLogHelper(this).handleCrash(this, exception, crashId)
                val test: Boolean =
                    smartLauncherRoot?.launcherPref?.getBoolean(
                        "crash_restart",
                        false
                    ) == true
                Log.e("test", test.toString() + "")
                defaultHandler.uncaughtException(
                    thread,
                    exception
                ) // this without crashAct call,without run delay,without SysExit works(doesn't hang)
        }
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