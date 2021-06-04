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

package com.outliers.smartlauncher.main.viewmodel

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import com.outliers.smartlauncher.core.SmartLauncherRoot
import com.outliers.smartlauncher.models.AppModel
import java.util.*
import kotlin.collections.ArrayList

class MainViewModel(application: Application, parent: MainVMParent) : ViewModel() {

    interface MainVMParent {
        fun refreshAppList(apps: ArrayList<AppModel>)
    }

    val smartLauncherRoot = SmartLauncherRoot.getInstance(application)
    var appListCopy: ArrayList<AppModel> = smartLauncherRoot?.allInstalledApps ?: ArrayList()
    var appList: ArrayList<AppModel> = appListCopy.clone() as ArrayList<AppModel>

    init {
        Log.d("test-VM", "allInstalled called")
        smartLauncherRoot?.let {
            it.liveAppModels.observeForever {
                appListCopy = smartLauncherRoot.allInstalledApps
                appList = appListCopy.clone() as ArrayList<AppModel>
                Log.d("test-observer", "appsList observer called in VM")
                parent.refreshAppList(appList)
            }
        }
    }

    fun searchTextChanged(s: String) {
        if (s.isEmpty()) {
            appList.clear()
            appList.addAll(appListCopy)
            return
        }
        appList.clear()
        for (appModel in appListCopy) {
            if (appModel.appName.toLowerCase(Locale.getDefault())
                    .contains(s.toLowerCase(Locale.getDefault()))
            )
                appList.add(appModel)
        }
    }

    fun onAppClicked(appModel: AppModel) {
        smartLauncherRoot?.appLaunched(appModel.packageName)
    }

    fun updateLocationCache(location: Location) {
        smartLauncherRoot?.currentLocation = location
        Log.v("test-updateLocCache", "$location")
    }
}