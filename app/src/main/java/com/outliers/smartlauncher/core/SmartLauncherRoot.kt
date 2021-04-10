package com.outliers.smartlauncher.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.service.autofill.FillEventHistory
import android.util.Log
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.models.AppModel.CREATOR.getAppModelsFromPackageInfoList
import com.outliers.smartlauncher.utils.Utils
import com.outliers.smartlauncher.utils.Utils.isValidString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class SmartLauncherRoot private constructor(val context: Context) {

    val appModels: ArrayList<AppModel> = ArrayList()
    val appToIdMap: ArrayMap<String, Int> = ArrayMap()  // package to hashcode map
    val launchSequence: ArrayList<String> = ArrayList(WINDOW_SIZE)  // sequence of last 'window size' package names
    val launchHistory: HashMap<Int, RealVector> = HashMap()
    val launcherPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    val appSuggestions = ArrayList<AppModel>(APP_SUGGESTION_COUNT)
    val appSuggestionsLiveData: MutableLiveData<ArrayList<AppModel>> = MutableLiveData()

    companion object {
        private var outliersLauncherRoot: SmartLauncherRoot? = null  // TODO warning--context in static field; memory leak.
        fun getInstance(context: Context): SmartLauncherRoot? {
            if (outliersLauncherRoot == null)
                outliersLauncherRoot = SmartLauncherRoot(context)
            return outliersLauncherRoot
        }
        const val WINDOW_SIZE = 3
        const val APP_SUGGESTION_COUNT = 5
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
        appModels.sortBy { it.appName.toLowerCase() }
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
        GlobalScope.launch {
            processAppSuggestion(packageName)
            if(launchSequence.size >=3)
                launchSequence.removeAt(0)  // remove oldest app history
            launchSequence.add(packageName)
            Log.v("test-lSeq", launchSequence.toString())
        }
    }

    suspend fun processAppSuggestion(packageName: String){
        withContext(Dispatchers.IO) {
            val launchVec = genAppLaunchVec(packageName)
        }
    }

    private suspend fun genAppLaunchVec(packageName: String): ArrayRealVector{
        /**
         * Time (hourOfDay), location, weekend, AM, BTheadset, wiredHeadset, charging,
         * cellularDataActive, wifiConnected, battery, ATF
         */

        val vecSize = 11 + allInstalledApps.size  // TODO this size will change when apps are installed or uninstalled. Need to handle such cases
        val launchVec: ArrayRealVector = ArrayRealVector(vecSize)
        Log.v("test-genLVec", launchVec.dimension.toString())
        val hourOfDay = Utils.getHourOfDay()
        launchVec.setEntry(0, hourOfDay/24.0)
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            val channel = Channel<Int>()
            // TODO add code to check quality of last known location; else request fresh location
            Log.v("test-location", "getting last loc")
            fusedLocationClient.lastLocation.addOnSuccessListener { location->
                launchVec.setEntry(1, location.latitude/360.0)
                launchVec.setEntry(2, location.longitude/360.0)
                Log.v("test-location", "offering 0")
                channel.offer(0)
            }
            channel.receive()
        }
        Log.v("test-location", "adding new features")
        val isWeekend: Double = (if (Utils.isTodayWeekend()) 1.0 else 0.0)
        launchVec.setEntry(3, isWeekend)
        Log.v("test-lvec", launchVec.toString())

        return launchVec
    }

    fun refreshAppList(){
        appModels.clear()
        allInstalledApps
    }
}