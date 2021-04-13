package com.outliers.smartlauncher.core

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.outliers.smartlauncher.models.AppModel
import java.util.*
import kotlin.collections.ArrayList

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val smartLauncherRoot = SmartLauncherRoot.getInstance(application)
    var appListCopy: ArrayList<AppModel> = smartLauncherRoot?.allInstalledApps ?: ArrayList()
    var appList: ArrayList<AppModel> = appListCopy.clone() as ArrayList<AppModel>

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

    fun onAppClicked(appModel: AppModel){
        smartLauncherRoot?.appLaunched(appModel.packageName)
    }
}