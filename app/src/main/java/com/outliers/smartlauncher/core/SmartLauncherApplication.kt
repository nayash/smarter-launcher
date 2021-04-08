package com.outliers.smartlauncher.core

import android.app.Application
import android.content.Context

class SmartLauncherApplication: Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        when(level){
            TRIM_MEMORY_RUNNING_LOW -> cleanAndBackUp()
        }
    }

    fun cleanAndBackUp(){
        // TODO resource/cache cleanup, if any
    }
}