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

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.debugtools.loghelper.LogHelper
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.models.AppModel.CREATOR.getAppModelsFromPackageInfoList
import com.outliers.smartlauncher.utils.Utils
import com.outliers.smartlauncher.utils.Utils.isValidString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.io.FileUtils
import org.apache.commons.math3.linear.ArrayRealVector
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.*

class SmartLauncherRoot private constructor(
    val context: Context,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val appModels: MutableList<AppModel> =
        Collections.synchronizedList(mutableListOf<AppModel>())  // TODO consider using CopyOnWriteArrayList !!
    val liveAppModels: MutableLiveData<MutableList<AppModel>> = MutableLiveData()
    val appToIdMap: ArrayMap<String, Int> =
        ArrayMap()  // package to hashcode map -- TODO no real use; consider removing
    val appToIdxMap: ArrayMap<String, Int> =
        ArrayMap()  // package to index map for ATF construction
    val idToApp: ArrayMap<Int, String> =
        ArrayMap()  // reverse Map of appToIdMap -- TODO no real use; consider removing
    var launchSequence: MutableList<String> =
        Collections.synchronizedList(mutableListOf<String>())  // sequence of last 'window size' package names

    // String key is used instead of int because int was being converted to String while reading
    // saved states and also "ambiguous overload error" while using function map.get(index)
    var launchHistoryList: LaunchHistoryList<String, ArrayRealVector> = LaunchHistoryList()
    val launcherPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    var appSuggestions = ArrayList<AppModel>(APP_SUGGESTION_COUNT)
    val appSuggestionsLiveData: MutableLiveData<ArrayList<AppModel>> = MutableLiveData()
    val logHelper = LogHelper.getLogHelper(context)
    var skip = false
    var currentLocation: Location? = null
    var scope: CoroutineScope = CoroutineScope(dispatcher) //CoroutineScope(Job() + dispatcher)

    companion object {
        private var outliersLauncherRoot: SmartLauncherRoot? = null

        fun getInstance(
            context: Context,
            dispatcher: CoroutineDispatcher = Dispatchers.IO
        ): SmartLauncherRoot? {
            if (outliersLauncherRoot == null) {
                Log.e("test-slRoot", "calling slRoot constructor")
                outliersLauncherRoot = SmartLauncherRoot(context, dispatcher)
            }
            return outliersLauncherRoot
        }

        const val WINDOW_SIZE = 3
        const val APP_SUGGESTION_COUNT = 8
        const val EXPLICIT_FEATURES_COUNT = 13
        const val APP_USAGE_DECAY_RATE = 0.5
        const val EPSILON = 0.1
        const val LOCATION_CACHE_LIFETIME_MILLIS = 5 * 60 * 1000 // 5 Mins
        const val HISTORY_MAX_SIZE = 2000
        var K = 20
        val distanceType = Constants.DISTANCE_TYPE_COSINE // or euclidean
    }

    init {
        // allInstalledApps
        // sizeTest()
        DataChangedBR().let {
            val intentFilter = IntentFilter(Constants.ACTION_LAUNCHER_DATA_REFRESH)
            context.registerReceiver(it, intentFilter)
        }
        loadState()
        Log.v("test-SLRootInit", "thread-${Thread.currentThread().id}")
    }

    val allInstalledApps: ArrayList<AppModel>
        get() {
            Log.d("test-allInstalledApps", appModels.size.toString())
            synchronized(appModels) {
                if (appModels.size == 0) {
                    //allInstalledApps.clear()
                    appModels.addAll(
                        getAppModelsFromPackageInfoList(
                            context.packageManager.getInstalledPackages(0),
                            context.packageManager.getInstalledApplications(0),
                            context
                        )
                    )
                    initPackageToIdMap()
                    sortApplicationsByName(appModels)
                    filterOutUnknownApps(appModels)
                    Log.d("test-allInstalledApps", "added--${appModels.size}")
                    liveAppModels.postValue(appModels)
                }
                return appModels.toList().toMutableList() as ArrayList<AppModel>
            }
        }

    fun sortApplicationsByName(appModels: MutableList<AppModel>) {
        // Collections.sort(appModels) { (appName), (appName) -> appName.compareTo(appName) }
        synchronized(appModels) {
            appModels.sortBy { it.appName.toLowerCase(Locale.getDefault()) }
        }
    }

    fun filterOutUnknownApps(models: MutableList<AppModel>) {
        synchronized(models) {
            val iterator = models.iterator()
            while (iterator.hasNext()) {
                val appModel = iterator.next()  // TODO ConcurrentModifEx here
                if (!isValidString(appModel.appName) || appModel.launchIntent == null)
                    iterator.remove()
            }
        }
    }

    fun initPackageToIdMap() {
        synchronized(appModels) {
            for ((idx: Int, app: AppModel) in appModels.withIndex()) {
                appToIdMap[app.packageName] = app.packageName.hashCode()
                appToIdxMap[app.packageName] = idx
                idToApp[appToIdMap[app.packageName]] = app.packageName
            }
        }
    }

    fun appLaunched(packageName: String) {
        if (skip)
            return
        scope.launch {
            try { // only to avoid crashes so that algorithm can be tested in real env. remove later and fix all crashes lazy bum
                Log.v("test-AppLaunched", "calling processAppSuggestion for $packageName")
                println("calling processAppSuggestion")
                processAppSuggestion(packageName)
                Log.v("test-lseq", "called processAppSuggestion")
                println("called processAppSuggestion")
                if (launchSequence.size >= WINDOW_SIZE)
                    launchSequence.removeAt(0)  // remove oldest app history
                launchSequence.add(packageName)
                Log.v("test-lSeq", launchSequence.toString())
                println("test-lSeq $launchSequence")
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Exception: ${ex.message}", Toast.LENGTH_LONG).show()
                }
                FirebaseCrashlytics.getInstance().recordException(ex)
                LogHelper.getLogHelper(context)?.addLogToQueue(
                    "appLaunchedException test- ${Log.getStackTraceString(ex)}",
                    LogHelper.LOG_LEVEL.ERROR,
                    context
                )
            }
        }
        return
    }

    suspend fun processAppSuggestion(packageName: String) {
        coroutineScope {
            val stime = System.currentTimeMillis()
            val launchVec = genAppLaunchVec(packageName)
            appSuggestions.clear()
            appSuggestions.addAll(getAppPreds(launchVec, packageName))
            notifyNewSuggestions()
            // once all calculations and prediction is done, store the launchVec as history,
            // to use it for future predictions
            if (appToIdMap[packageName] == null)
                Log.e(
                    "test-packageNull",
                    "$packageName --- ${appToIdxMap.size}, ${allInstalledApps.size}"
                )
            // appToIdMap[packageName]?.let { launchHistory.put(it, launchVec) }
            launchHistoryList.add(packageName, launchVec)
            Log.v("test-launchHistorySize", launchHistoryList.size.toString())
            val duration = (System.currentTimeMillis() - stime) / 1000
            Log.d("test-processTime", "processing duration = $duration secs")
        }
    }

    private suspend fun findKNN(
        queryVec: ArrayRealVector,
        dataset: LaunchHistoryList<String, ArrayRealVector>
    ): MutableList<Pair<Tuple<String, ArrayRealVector>, Double>> {
        val similarityMap: ArrayMap<Tuple<String, ArrayRealVector>, Double> = ArrayMap()
        coroutineScope {
            Log.v("test-findKNN", "starting loop")
            for (tuple in dataset) {
                if (distanceType.equals(Constants.DISTANCE_TYPE_COSINE)) {
                    similarityMap[tuple] = tuple.value.cosine(queryVec) // problem
                } else {
                    similarityMap[tuple] = 1 / (tuple.value.getDistance(queryVec) + EPSILON)
                }
            }
            Log.v("test-findKNN", "loop end")
        }
        Log.v("test-findKNN", "result ${similarityMap.keys.size} pairs")
        return similarityMap.toList().sortedBy { (k, v) -> -v }
            .subList(0, min(K, similarityMap.size)).toMutableList()
    }

    private suspend fun getAppPreds(
        launchVec: ArrayRealVector,
        launchPackageName: String
    ): ArrayList<AppModel> {
        val appPreds = ArrayList<AppModel>()
        coroutineScope {
            Log.v("test-getAppPreds", "thread-${Thread.currentThread().id}")
            val knn = findKNN(launchVec, launchHistoryList)
            LogHelper.getLogHelper(context)?.addLogToQueue(
                "test-getAppPreds- KNN result (${knn.size}) = ${
                    knn.map {
                        Pair(
                            it.first.key,
                            it.second
                        )
                    }
                }", LogHelper.LOG_LEVEL.INFO, context
            )
            var appScoresMap = mutableMapOf<String, Double>()
            // val appFreq = HashMap<String, Int>()
            for (pair in knn) {
                val tuple = pair.first
                val similarity = pair.second
                // val lVecHist = tuple.value
                // Log.v("test-dimCheck", "${lVecHist.dimension}, ${launchVec.dimension}")
                appScoresMap[tuple.key] = (appScoresMap[tuple.key] ?: 0.0) + similarity
                // appFreq[tuple.key] = (appFreq[tuple.key] ?: 0) + 1
            }

            // to use avg score instead of sums
            /*for ((k, v) in appScoresMap) {
                appScoresMap[k] = v / (appFreq[k] ?: 1)
            }*/

            var breaker = 0
            Log.v("test-appScores", appScoresMap.toString())

            appScoresMap =
                appScoresMap.toList().sortedBy { (k, v) -> -v }.toMap().toMutableMap()
            LogHelper.getLogHelper(context)?.addLogToQueue(
                "test-appScoreSorted-${appScoresMap}",
                LogHelper.LOG_LEVEL.INFO,
                context
            )
            for ((packageName, score) in appScoresMap) {
                if (packageName.equals(launchPackageName))
                    continue
                Utils.getAppByPackage(allInstalledApps, packageName)?.let { appPreds.add(it) }
                breaker++
                Log.v("test-app-predictions", "$packageName-->$score")
                if (breaker >= APP_SUGGESTION_COUNT)
                    break
            }
        }
        println("getAppPreds return ${appPreds.size}, $appPreds")
        return appPreds
    }

    private suspend fun genAppLaunchVec(packageName: String): ArrayRealVector {
        /**
         * DayOfMonth (not in paper), Time (hourOfDay), location, weekend, AM, BTheadset, wiredHeadset, charging,
         * cellularDataActive, wifiConnected, battery, ATF
         */
        val vecSize = EXPLICIT_FEATURES_COUNT + allInstalledApps.size
        // println("genAppLaunchVec- $vecSize, $EXPLICIT_FEATURES_COUNT, ${allInstalledApps.size}")
        val launchVec = ArrayRealVector(vecSize)
        coroutineScope {
            Log.v("test-genLVec", launchVec.dimension.toString())
            var featureIdx = 0
            launchVec.setEntry(featureIdx++, Utils.getDayOfMonth() / 31.0)
            val hourOfDay = Utils.getHourOfDay() / 24.0
            launchVec.setEntry(featureIdx++, 0.5 + 0.5 * sin(2 * Math.PI * hourOfDay))
            launchVec.setEntry(featureIdx++, 0.5 + 0.5 * cos(2 * Math.PI * hourOfDay))

            val diff = System.currentTimeMillis() - (currentLocation?.time ?: 0)
            Log.v("test-cacheDiff", "$diff")
            if (diff / 1000 > LOCATION_CACHE_LIFETIME_MILLIS) {
                // locationCache is old; request fresh
                val fusedLocationClient: FusedLocationProviderClient =
                    LocationServices.getFusedLocationProviderClient(context)
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val channel = Channel<Int>()
                    if (Utils.isLocationEnabled(context)) {
                        Log.v("test-location", "getting loc")
                        /*fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            launchVec.setEntry(featureIdx++, location.latitude / 360.0)
                            launchVec.setEntry(featureIdx++, location.longitude / 360.0)
                            Log.v("test-location", "offering 0")
                            channel.offer(0) }*/
                        fusedLocationClient.getCurrentLocation(
                            LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
                            null
                        ).addOnSuccessListener { location ->
                            Log.d("test-location", location?.toString() + ",")
                            location?.let {
                                launchVec.setEntry(
                                    featureIdx++,
                                    Utils.minMaxScale(location.latitude, -90.0, 90.0, 0.0, 1.0)
                                )
                                launchVec.setEntry(
                                    featureIdx++,
                                    Utils.minMaxScale(location.longitude, -180.0, 180.0, 0.0, 1.0)
                                )
                                Log.v("test-location", "offering 0")
                            }
                            channel.offer(0)
                        }
                        channel.receive()
                    }
                }
            } else {
                currentLocation?.let {
                    launchVec.setEntry(
                        featureIdx++,
                        Utils.minMaxScale(it.latitude, -90.0, 90.0, 0.0, 1.0)
                    )
                    launchVec.setEntry(
                        featureIdx++,
                        Utils.minMaxScale(it.longitude, -180.0, 180.0, 0.0, 1.0)
                    )
                    Log.v("test-location", "using cached loc: $currentLocation")
                }
            }
            Log.v("test-locationDone", "adding new features")
            val isWeekend: Double = (if (Utils.isTodayWeekend()) 1.0 else 0.0)
            launchVec.setEntry(featureIdx++, isWeekend)
            val isAM: Double = if (Utils.isHourAM(Utils.getHourOfDay())) 1.0 else 0.0
            launchVec.setEntry(featureIdx++, isAM)
            val isBTHeadset: Double = if (Utils.isBluetoothHeadsetConnected()) 1.0 else 0.0
            launchVec.setEntry(featureIdx++, isBTHeadset)
            val isWiredHeadset: Double = if (Utils.isWiredHeadsetConnected(context)) 1.0 else 0.0
            launchVec.setEntry(featureIdx++, isWiredHeadset)
            val isChargin: Double = if (Utils.isCharging(context)) 1.0 else 0.0
            launchVec.setEntry(featureIdx++, isChargin)
            val isMobileData: Double = if (Utils.isMobileDataConnected(context)) 1.0 else 0.0
            launchVec.setEntry(featureIdx++, isMobileData)
            val isWifi: Double = if (Utils.isWifiConnected(context)) 1.0 else 0.0
            launchVec.setEntry(featureIdx++, isWifi)
            Utils.getBatteryLevel(context)?.div(100.0)?.let { launchVec.setEntry(featureIdx, it) }

            for ((i, appPackage) in launchSequence.asReversed().withIndex()) {
                /**
                 * if app launch history for window=3 is (oldest to latest) [instagram, facebook, Maps] then this loop
                 * iterates in reversed order (latest to oldest) and calculates ATF values and set to corresponding app index
                 */
                // appIdx could be null in cases where one of the apps in the launch sequence has been uninstalled
                val appIdx = appToIdxMap[appPackage]
                if (appIdx == null)
                    continue
                val appValue = APP_USAGE_DECAY_RATE.pow(i)

                launchVec.setEntry(
                    EXPLICIT_FEATURES_COUNT + appIdx,
                    appValue
                )
                Log.d("test-ATF-val", "$packageName, $appIdx, $EXPLICIT_FEATURES_COUNT, $appValue")
            }
            Log.v("test-appToIdx", appToIdxMap.toString())
            Log.v("test-lvec", launchVec.toString())
        }
        return launchVec
    }

    private fun notifyNewSuggestions() {
        appSuggestionsLiveData.postValue(appSuggestions)
    }

    fun refreshAppList(eventType: Int, packageName: String?) {
        scope.launch {
            appModels.clear()
            Log.d("test-refreshAppList", appModels.size.toString())
            allInstalledApps
            skip = true

            if (eventType == 1) {// new app installed
                val vecTemp = launchHistoryList.getValueAt(0)
                vecTemp?.let {
                    val oldSize = vecTemp.dimension - EXPLICIT_FEATURES_COUNT
                    Log.d(
                        "test-refreshAppList",
                        "event installed if check--$oldSize, ${allInstalledApps.size}"
                    )
                    if (oldSize + 1 != allInstalledApps.size) {
                        // something unexpected happened. log it!
                        FirebaseCrashlytics.getInstance().log(
                            "installed package prob: $packageName," +
                                    " old_size: $oldSize, new_size: ${allInstalledApps.size}"
                        )
                        Log.d("test-installedPkgProb","$packageName," +
                                " old_size: $oldSize, new_size: ${allInstalledApps.size}")
                        FirebaseCrashlytics.getInstance()
                            .recordException(Exception("Got app_install event but size is same or package exists"))
                    } else {
                        packageName?.let { addNewDimensionToHistory(it) }
                    }
                }
            } else { // app uninstalled
                val vecTemp = launchHistoryList.getValueAt(0)
                vecTemp?.let {
                    val oldSize = vecTemp.dimension - EXPLICIT_FEATURES_COUNT
                    Log.d(
                        "test-refreshAppList",
                        "event uninstalled if check--$oldSize, ${allInstalledApps.size}"
                    )
                    if (oldSize - 1 != allInstalledApps.size) {
                        // something unexpected happened. log it!
                        FirebaseCrashlytics.getInstance().log(
                            "uninstalled package prob: $packageName," +
                                    " old_size: $oldSize, new_size: ${allInstalledApps.size}"
                        )
                        Log.d("uninstalledPkgProb","$packageName," +
                                " old_size: $oldSize, new_size: ${allInstalledApps.size}")
                        FirebaseCrashlytics.getInstance()
                            .recordException(Exception("Got app_uninstall event but size is same or package exists"))
                    } else {
                        packageName?.let { removeOldDimension(it) }
                    }
                }
            }

            Log.v(
                "test-dimCheckPostMod", "${launchHistoryList.getValueAt(0)?.dimension}," +
                        "${launchHistoryList.size}"
            )

            skip = false
        }
    }

    private suspend fun addNewDimensionToHistory(packageName: String) {
        appToIdxMap[packageName] = appToIdxMap.size
        appToIdMap[packageName] = packageName.hashCode()
        idToApp[packageName.hashCode()] = packageName
        Log.v("test-addNewDim", "called")
        coroutineScope {
            synchronized(launchHistoryList) {
                for ((i, tuple) in launchHistoryList.withIndex()) {
                    val vector = tuple.value
                    vector.let {
                        val newVec = vector.append(0.0) as ArrayRealVector
                        Log.v("test-newVec", "${newVec.dimension}")
                        launchHistoryList.updateValueAt(i, newVec)
                    }
                }
            }
        }
        saveState()
    }

    private suspend fun removeOldDimension(packageName: String) {
        Log.v("test-removeOldDim", "called")
        coroutineScope {
            val idxToRemove = appToIdxMap[packageName]
            idxToRemove?.let {
                synchronized(launchHistoryList) {
                    for ((i, tuple) in launchHistoryList.withIndex()) {
                        val vector = tuple.value
                        vector.let {
                            val newVec = vector.getSubVector(0, idxToRemove).append(
                                vector.getSubVector(
                                    idxToRemove + 1,
                                    vector.dimension - (idxToRemove + 1)
                                )
                            )
                            launchHistoryList.updateValueAt(i, newVec as ArrayRealVector)
                        }
                    }
                }
            }
            appToIdMap.remove(packageName)
            appToIdxMap.remove(packageName)
            idToApp.remove(packageName.hashCode())
        }
        saveState()
    }

    private fun saveState() {
        scope.launch {
            saveLaunchSequence()
            saveLaunchHistory()
            savePreds()

            LogHelper.getLogHelper(context)?.addLogToQueue(
                "saveState called: " +
                        "launchSeq=${launchSequence.size}" +
                        ", histSize=${launchHistoryList.size}, preds=${appSuggestions.size}",
                LogHelper.LOG_LEVEL.INFO, "SLRoot"
            )
        }
    }

    fun cleanUp() {
        saveState()
    }

    fun loadState() {
        scope.launch {
            loadLaunchSequence()
            loadLaunchHistory()
            loadPreds()

            LogHelper.getLogHelper(context)?.addLogToQueue(
                "test-loadState called: " +
                        "launchSeq=${launchSequence.size}" +
                        ", histSize=${launchHistoryList.size}, preds=${appSuggestions.size}",
                LogHelper.LOG_LEVEL.INFO, "SLRoot"
            )
        }
    }

    suspend fun saveLaunchSequence() {
        if (launchSequence.size == 0)
            return
        withContext(dispatcher) { // to enforce this function to be "suspend" type
            val fileLaunchSeq = File(
                Utils.getAppDataFolderInternal(context),
                Constants.LAUNCH_SEQUENCE_SAVE_FILE
            )
            // Utils.writeToFile(context, fileLaunchSeq.absolutePath, launchSequence)
            FileUtils.writeStringToFile(fileLaunchSeq, Gson().toJson(launchSequence).toString())
        }
    }

    suspend fun saveLaunchHistory() {
        if (launchHistoryList.isEmpty())
            return
        withContext(dispatcher) {
            val file = File(
                Utils.getAppDataFolderInternal(context),
                Constants.LAUNCH_HISTORY_SAVE_FILE
            )
            // Utils.writeToFile(context, file.absolutePath, launchHistoryList)
            FileUtils.writeStringToFile(file, Gson().toJson(launchHistoryList).toString())
            // launcherPref.edit().putString(Constants.LAUNCH_HISTORY_SAVE_FILE, Gson().toJson(launchHistory)).apply()
            Log.v("test-launchHistorySave", Gson().toJson(launchHistoryList))
        }
    }

    suspend fun savePreds() {
        if (appSuggestions.isEmpty())
            return
        withContext(dispatcher) {
            val file = File(
                Utils.getAppDataFolderInternal(context),
                Constants.APP_SUGGESTIONS_SAVE_FILE
            )
            val temp: List<String> = appSuggestions.map { it.packageName }
            // Utils.writeToFile(context, file.absolutePath, temp)
            FileUtils.writeStringToFile(file, Gson().toJson(temp).toString())
        }
    }

    suspend fun loadLaunchSequence() {
        val fileLaunchSeq = File(
            Utils.getAppDataFolderInternal(context),
            Constants.LAUNCH_SEQUENCE_SAVE_FILE
        )
        if (!fileLaunchSeq.exists())
            return

        withContext(dispatcher) {
            // val temp = Utils.readFromFile<ArrayList<String>>(context, fileLaunchSeq.absolutePath)
            try {
                launchSequence.clear()
                val jArray = JSONArray(FileUtils.readFileToString(fileLaunchSeq))
                for (i in 0 until jArray.length()) {
                    launchSequence.add(jArray.getString(i))
                }
            } catch (ex: JSONException) {
                LogHelper.getLogHelper(context)?.addLogToQueue(
                    "test-loadLaunchSeq -- ${Log.getStackTraceString(ex)}",
                    LogHelper.LOG_LEVEL.ERROR, context
                )
            }
            Log.d("test-loadLaunchSeq", "$launchSequence")
        }
    }

    suspend fun loadLaunchHistory() {
        // TODO handle the scenario where saved file launchVec size is different from current installed apps size
        // one option could be save a list of installed apps along with history and on loading check
        // which app is missing or extra
        val file = File(
            Utils.getAppDataFolderInternal(context),
            Constants.LAUNCH_HISTORY_SAVE_FILE
        )
        if (!file.exists())
            return
        // val checkPoint = Utils.readFromFileAsString(context, file.absolutePath)
        val checkPoint = FileUtils.readFileToString(file)
        //{"com.google.android.calendar":{"data":[0.41935483870967744,0.041666666666666664,0.03597791333333333,0.21588130694444443,0.0,1.0,0.0,0.0,1.0,0.0,0.97,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]},"com.google.android.deskclock":{"data":[0.41935483870967744,0.041666666666666664,0.03597791861111111,0.2158812911111111,0.0,1.0,0.0,0.0,1.0,0.0,0.97,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}}
        // [{"key":"0","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"1","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"2","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"3","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"4","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"5","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"6","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"7","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"8","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"9","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}}]
        // val checkPoint = launcherPref.getString(Constants.LAUNCH_HISTORY_SAVE_FILE, "")
        Log.v("test-lhLoad", "checkpoint= $checkPoint")
        if (checkPoint != null && checkPoint.isNotEmpty()) {
            try {
                val temp = JSONArray(checkPoint)
                launchHistoryList.clear()
                for (i in 0 until temp.length()) {
                    val tupleJObj = temp.getJSONObject(i)
                    val key = tupleJObj.getString("key")
                    val value = tupleJObj.getJSONObject("value").getJSONArray("data")
                    val vec = ArrayRealVector(value.length())
                    for (j in 0 until value.length()) {
                        vec.setEntry(j, value.getDouble(j))
                    }
                    launchHistoryList.add(key, vec)
                }
                Log.v("test-launchHistoryLoad", "${temp.length()}, ${launchHistoryList.size}")
                cleanUpHistory()  // TODO review if this is correct place to perform clean up or should it be scheduled
                K = max(Constants.MIN_K, sqrt(launchHistoryList.size.toDouble()).toInt())
                LogHelper.getLogHelper(context)
                    ?.addLogToQueue("test-value of k=$K", LogHelper.LOG_LEVEL.INFO, context)
            } catch (ex: JSONException) {
                Log.e("test-lhJSON", Log.getStackTraceString(ex))
                FirebaseCrashlytics.getInstance().recordException(ex)
            }
        }
        if (launchHistoryList.isNotEmpty())
            Log.v("test-launchHistoryLoad", "$launchHistoryList")
    }

    suspend fun loadPreds() {
        val file = File(
            Utils.getAppDataFolderInternal(context),
            Constants.APP_SUGGESTIONS_SAVE_FILE
        )
        if (!file.exists()) {
            appSuggestionsLiveData.postValue(appSuggestions)
            return
        }
        // val temp = Utils.readFromFile<ArrayList<String>>(context, file.absolutePath)
        withContext(dispatcher) {
            try {
                val temp = JSONArray(FileUtils.readFileToString(file))
                appSuggestions.clear()
                if (temp != null) {
                    for (i in 0 until temp.length()) {
                        val packageName = temp.getString(i)
                        Log.d("test-loadPreds", "loop")
                        for (appModel in allInstalledApps) {
                            // Log.d("test-loadPreds", "inside loop")
                            if (appModel.packageName.equals(packageName, true))
                                appSuggestions.add(appModel)
                        }
                    }
                }
            } catch (ex: JSONException) {
                LogHelper.getLogHelper(context)?.addLogToQueue(
                    "test-loadPreds -- ${Log.getStackTraceString(ex)}",
                    LogHelper.LOG_LEVEL.ERROR,
                    context
                )
            }
            appSuggestionsLiveData.postValue(appSuggestions)  // TODO not notifying on device restart
            Log.d("test-loadPreds", "$appSuggestions")
        }
    }

    suspend fun cleanUpHistory() {
        Log.v("test-cleanUpHist", "${launchHistoryList.size}, $HISTORY_MAX_SIZE")
        if (launchHistoryList.size > HISTORY_MAX_SIZE) {
            Log.v("test-hist", "$launchHistoryList")
            coroutineScope {
                val topHalf: Int = launchHistoryList.size / 2
                val counterMap = ArrayMap<String, Int>() // package to frequency map
                for (i in 0 until topHalf) {  // look into the top oldest records only
                    val packageName = launchHistoryList[i].key
                    counterMap[packageName] = counterMap.getOrDefault(packageName, 0) + 1
                }
                /*counterMap = counterMap.entries.sortedBy { -it.value }
                    .associate { it.toPair() } as ArrayMap<String, Int>*/
                counterMap.toList().sortedBy { (key, value) -> value }
                    .toMap(counterMap)
                Log.v("test-counter", "$counterMap, threshold=${0.01 * HISTORY_MAX_SIZE}")
                println(("test-counter $counterMap, threshold=${0.01 * HISTORY_MAX_SIZE}"))
                for ((packageName, frequency) in counterMap) {
                    if (frequency < 0.01 * HISTORY_MAX_SIZE) { // frequency less that 1% of MAX size
                        launchHistoryList.removeEntriesWithKey(packageName)
                        Log.v(
                            "test-cleanUpHistory",
                            "deleted package: $packageName with $frequency launches"
                        )
                        println("test-cleanUpHistory deleted package: $packageName with $frequency launches")
                        println("test-launcherSize ${launchHistoryList.size}")
                    }
                }

                if (FirebaseApp.getApps(context).isNotEmpty())
                    FirebaseCrashlytics.getInstance()
                        .log("History CleanUp done: ${launchHistoryList.size}")
            }
        }
    }

    class DataChangedBR : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("test-onReceive", "${intent?.action}")
            outliersLauncherRoot?.loadState()
        }
    }
}