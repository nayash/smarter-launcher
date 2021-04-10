package com.outliers.smartlauncher.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.service.autofill.FillEventHistory
import android.util.Log
import androidx.collection.ArrayMap
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.models.AppModel.CREATOR.getAppModelsFromPackageInfoList
import com.outliers.smartlauncher.utils.Utils.isValidString
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class SmartLauncherRoot private constructor(val context: Context) {

    val appModels: ArrayList<AppModel> = ArrayList()
    val appToIdMap: ArrayMap<String, Int> = ArrayMap()  // package to hashcode map
    val launchSequence: ArrayList<String> = ArrayList(WINDOW_SIZE)  // sequence of last 'window size' package names
    val launchHistory: HashMap<Int, RealVector> = HashMap()
    val launcherPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    /*appModels = AppModel.getAppModelsFromAppInfoList(context.getPackageManager().
                    getInstalledApplications(0), context);*/

    companion object {
        private var outliersLauncherRoot: SmartLauncherRoot? = null  // TODO warning--context in static field; memory leak.
        fun getInstance(context: Context): SmartLauncherRoot? {
            if (outliersLauncherRoot == null)
                outliersLauncherRoot = SmartLauncherRoot(context)
            return outliersLauncherRoot
        }
        const val WINDOW_SIZE = 3
    }

    init {
        initPackageToIdMap(allInstalledApps, appToIdMap)
    }

    val allInstalledApps: ArrayList<AppModel>
        get() {  // TODO instead of querying installed apps all the time, implement install/uninstall listeners and update only in such events?
            if (appModels.size == 0) {
                appModels.clear()
                appModels.addAll(
                    getAppModelsFromPackageInfoList(
                        context.packageManager.getInstalledPackages(0),
                        context.packageManager.getInstalledApplications(0),
                        context
                    )
                )
                sortApplicationsByName(appModels)
                filterOutUnknownApps(appModels)
                Log.v("test-Apps", appModels.size.toString())
            }
            return appModels
        }

    fun sortApplicationsByName(appModels: ArrayList<AppModel>) {
        // Collections.sort(appModels) { (appName), (appName) -> appName.compareTo(appName) }
        appModels.sortBy { it.appName }
    }

    fun filterOutUnknownApps(models: ArrayList<AppModel>) {
        val iterator = models.iterator()
        while (iterator.hasNext()) {
            val appModel = iterator.next()
            if (!isValidString(appModel.appName) || appModel.launchIntent == null)
                iterator.remove()
        }
    }

    fun initPackageToIdMap(appList: ArrayList<AppModel>, appToIdMap: MutableMap<String, Int>){
        for(app: AppModel in appList){
            appToIdMap[app.packageName] = app.packageName.hashCode()
        }
    }

    fun appLaunched(packageName: String){
        processAppSuggestion(packageName)
        if(launchSequence.size >=3)
            launchSequence.removeAt(0)  // remove oldest app history
        launchSequence.add(packageName)
        Log.v("test-lSeq", launchSequence.toString())
    }

    fun processAppSuggestion(packageName: String){
        val launchVec = genAppLaunchVec(packageName)
    }

    fun genAppLaunchVec(packageName: String): ArrayRealVector{
        /**
         * Time, location, weekend, AM, BTheadset, wiredHeadset, charging,
         * cellularDataActive, wifiConnected, battery, ATF
         */

        val vecSize = 11 + allInstalledApps.size  // TODO this size will change when apps are installed or uninstalled. Need to handle such cases
        val launchVec: ArrayRealVector = ArrayRealVector(vecSize)
        Log.v("test-genLVec", launchVec.dimension.toString())
        return launchVec
    }

    fun refreshAppList(){
        appModels.clear()
        allInstalledApps
    }
}