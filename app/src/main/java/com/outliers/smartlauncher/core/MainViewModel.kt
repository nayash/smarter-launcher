package com.outliers.smartlauncher.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.outliers.smartlauncher.models.AppModel
import java.util.*
import kotlin.collections.ArrayList

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    var appList: ArrayList<AppModel> =
        SmartLauncherRoot.getInstance(application)?.allInstalledApps ?: ArrayList()
    var appListCopy: ArrayList<AppModel> = appList.clone() as ArrayList<AppModel>

    fun searchTextChanged(s: String){
        if (s.isEmpty()) {
            appList.clear()
            appList.addAll(appListCopy)
            return
        }
        appList.clear()
        for (appModel in appListCopy) {
            if (appModel.appName.toLowerCase(Locale.getDefault())
                    .contains(s.toLowerCase(Locale.getDefault())))
                        appList.add(appModel)
        }
    }
}