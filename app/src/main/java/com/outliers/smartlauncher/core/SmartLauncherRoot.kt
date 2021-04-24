    package com.outliers.smartlauncher.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.models.AppModel.CREATOR.getAppModelsFromPackageInfoList
import com.outliers.smartlauncher.utils.LogHelper
import com.outliers.smartlauncher.utils.Utils
import com.outliers.smartlauncher.utils.Utils.isValidString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.math3.linear.ArrayRealVector
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.pow

class SmartLauncherRoot private constructor(val context: Context,
                                            val dispatcher: CoroutineDispatcher = Dispatchers.IO) {

    val appModels: MutableList<AppModel> = Collections.synchronizedList(mutableListOf<AppModel>())
    val appToIdMap: ArrayMap<String, Int> = ArrayMap()  // package to hashcode map -- TODO no real use; consider removing
    val appToIdxMap: ArrayMap<String, Int> =
        ArrayMap()  // package to index map for ATF construction
    val idToApp: ArrayMap<Int, String> = ArrayMap()  // reverse Map of appToIdMap -- TODO no real use; consider removing
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

    companion object {
        private var outliersLauncherRoot: SmartLauncherRoot? =
            null  // TODO warning--context in static field; memory leak.

        fun getInstance(context: Context,
                        dispatcher: CoroutineDispatcher=Dispatchers.IO): SmartLauncherRoot? {
            if (outliersLauncherRoot == null) {
                Log.e("test-slRoot", "calling slRoot constructor")
                outliersLauncherRoot = SmartLauncherRoot(context, dispatcher)
            }
            return outliersLauncherRoot
        }

        const val WINDOW_SIZE = 3
        const val APP_SUGGESTION_COUNT = 8
        const val EXPLICIT_FEATURES_COUNT = 11
        const val APP_USAGE_DECAY_RATE = 0.5
        const val EPSILON = 0.1
        const val LOCATION_CACHE_LIFETIME_MILLIS = 5 * 60 * 1000 // 5 Mins
        const val HISTORY_MAX_SIZE = 2000
    }

    init {
        // allInstalledApps
        // sizeTest()
        loadState()
    }

    val allInstalledApps: ArrayList<AppModel>
        get() {
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
                Log.v("test-Apps", appModels.size.toString())
            }
            return appModels.toList() as ArrayList<AppModel>
        }

    fun sortApplicationsByName(appModels: MutableList<AppModel>) {
        // Collections.sort(appModels) { (appName), (appName) -> appName.compareTo(appName) }
        appModels.sortBy { it.appName.toLowerCase() }
    }

    fun filterOutUnknownApps(models: MutableList<AppModel>) {
        val iterator = models.iterator()
        while (iterator.hasNext()) {
            val appModel = iterator.next()
            if (!isValidString(appModel.appName) || appModel.launchIntent == null)
                iterator.remove()
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
        GlobalScope.launch {
            Log.v("test-lseq", "calling processAppSuggestion")
            println("calling processAppSuggestion")
            processAppSuggestion(packageName)
            Log.v("test-lseq", "called processAppSuggestion")
            println("called processAppSuggestion")
            if (launchSequence.size >= 3)
                launchSequence.removeAt(0)  // remove oldest app history
            launchSequence.add(packageName)
            Log.v("test-lSeq", launchSequence.toString())
            println("test-lSeq $launchSequence")
        }
        return
    }

    suspend fun processAppSuggestion(packageName: String) {
        withContext(dispatcher) {
            val stime = System.currentTimeMillis()
            val launchVec = genAppLaunchVec(packageName)
            appSuggestions.clear()
            appSuggestions.addAll(findKNN(launchVec))
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

    private suspend fun findKNN(launchVec: ArrayRealVector): ArrayList<AppModel> {
        val appPreds = ArrayList<AppModel>()
        var appScoresMap = HashMap<String, Double>()  // appPackage to score mapping, to later apply toSrotedMap
        for (tuple in launchHistoryList) {
            val lVecHist = tuple.value
            if(lVecHist != null) {
                Log.v("test-dimCheck", "${lVecHist.dimension}, ${launchVec.dimension}")
                val distance = lVecHist.getDistance(launchVec)
                val similarity = 1 / (distance + EPSILON)
                var prevScore = appScoresMap[tuple.key] ?: 0.0
                prevScore += similarity
                appScoresMap[tuple.key] = prevScore
            }
        }
        var breaker = 0
        Log.v("test-appScores", appScoresMap.toString())
        appScoresMap = appScoresMap.entries.sortedBy { -it.value }
            .associate { it.toPair() } as HashMap<String, Double>
        Log.v("test-appScoreSorted", appScoresMap.toString())
        for ((packageName, score) in appScoresMap) { // TODO IMP!!!! this is wrong, sort by value not key
            Utils.getAppByPackage(allInstalledApps, packageName)?.let { appPreds.add(it) }
            breaker++
            Log.v("test-app-predictions", "$packageName-->$score")
            if (breaker >= APP_SUGGESTION_COUNT)
                break
        }
        return appPreds
    }

    private suspend fun genAppLaunchVec(packageName: String): ArrayRealVector {
        /**
         * DayOfMonth (not in paper), Time (hourOfDay), location, weekend, AM, BTheadset, wiredHeadset, charging,
         * cellularDataActive, wifiConnected, battery, ATF
         */
        // TODO this size will change when apps are installed or uninstalled. Need to handle such cases: one possible
        // approach is to remove the appIdx from all launch vectors (for uninstall) and for new installations, add app to bottom of array
        val vecSize = EXPLICIT_FEATURES_COUNT + allInstalledApps.size
        val launchVec = ArrayRealVector(vecSize)
        runBlocking {
            Log.v("test-genLVec", launchVec.dimension.toString())
            var featureIdx = 0
            launchVec.setEntry(featureIdx++, Utils.getDayOfMonth() / 31.0)
            val hourOfDay = Utils.getHourOfDay()
            launchVec.setEntry(featureIdx++, hourOfDay / 24.0)

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
                                launchVec.setEntry(featureIdx++, location.latitude / 360.0)
                                launchVec.setEntry(featureIdx++, location.longitude / 360.0)
                                Log.v("test-location", "offering 0")
                            }
                            channel.offer(0)
                        }
                        channel.receive()
                    }
                }
            } else {
                currentLocation?.let {
                    launchVec.setEntry(featureIdx++, it.latitude / 360.0)
                    launchVec.setEntry(featureIdx++, it.longitude / 360.0)
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
            Utils.getBatteryLevel(context)?.div(100.0)?.let { launchVec.setEntry(10, it) }

            for ((i, appPackage) in launchSequence.asReversed().withIndex()) {
                /**
                 * if app launch history for window=3 is (oldest to latest) [instagram, facebook, Maps] then this loop
                 * iterates in reversed order (latest to oldest) and calculates ATF values and set to corresponding app index
                 */
                /**
                 * if app launch history for window=3 is (oldest to latest) [instagram, facebook, Maps] then this loop
                 * iterates in reversed order (latest to oldest) and calculates ATF values and set to corresponding app index
                 */
                val appIdx = appToIdxMap[appPackage]
                val appValue = APP_USAGE_DECAY_RATE.pow(i)
                // TODO convert this to nullable expression. Package should be availabe in hashmap if not it should crash
                if (appIdx == null)
                    Log.e(
                        "test-packageNull",
                        "$appPackage --- ${appToIdxMap.size}, ${allInstalledApps.size}"
                    )
                launchVec.setEntry(
                    EXPLICIT_FEATURES_COUNT + appIdx!!,
                    appValue
                )  // TODO IMP!!! crash here: DefaultDispatcher-worker-3 Process: com.outliers.smartlauncher, PID: 15033 java.lang.NullPointerException
                Log.d("test-ATF-val", "$packageName, $appIdx, $EXPLICIT_FEATURES_COUNT, $appValue")
            }
            Log.v("test-appToIdx", appToIdxMap.toString())
            Log.v("test-lvec", launchVec.toString())
        }
        return launchVec
    }

    fun notifyNewSuggestions() {
        appSuggestionsLiveData.postValue(appSuggestions)
    }

    fun refreshAppList(eventType: Int, packageName: String?) {
        GlobalScope.launch {
            appModels.clear()
            allInstalledApps
            skip = true

            if (eventType == 1) {// new app installed
                val vecTemp = launchHistoryList.getValueAt(0)
                vecTemp?.let {
                    val oldSize = vecTemp.dimension - EXPLICIT_FEATURES_COUNT
                    if (oldSize + 1 != allInstalledApps.size || appToIdMap.contains(packageName)) {
                        // something unexpected happened. log it!
                        FirebaseCrashlytics.getInstance().log(
                            "installed package prob: $packageName," +
                                    " old_size: $oldSize, new_size: ${allInstalledApps.size}"
                        )
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
                    if (oldSize - 1 != allInstalledApps.size || !appToIdMap.contains(packageName)) {
                        // something unexpected happened. log it!
                        FirebaseCrashlytics.getInstance().log(
                            "uninstalled package prob: $packageName," +
                                    " old_size: $oldSize, new_size: ${allInstalledApps.size}"
                        )
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

    suspend fun addNewDimensionToHistory(packageName: String) {
        appToIdxMap[packageName] = appToIdxMap.size
        appToIdMap[packageName] = packageName.hashCode()
        idToApp[packageName.hashCode()] = packageName
        Log.v("test-addNewDim", "called")
        withContext(dispatcher) {
            synchronized(launchHistoryList) {
                for ((i, tuple) in launchHistoryList.withIndex()) {
                    val vector = tuple.value
                    vector?.let {
                        val newVec = vector.append(0.0) as ArrayRealVector
                        Log.v("test-newVec", "${newVec.dimension}")
                        launchHistoryList.updateValueAt(i, newVec)
                    }
                }
            }
        }
        saveState()
    }

    suspend fun removeOldDimension(packageName: String) {
        Log.v("test-removeOldDim", "called")
        withContext(dispatcher) {
            val idxToRemove = appToIdxMap[packageName]
            idxToRemove?.let {
                synchronized(launchHistoryList) {
                    for ((i, tuple) in launchHistoryList.withIndex()) {
                        val vector = tuple.value
                        vector?.let {
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

    fun sizeTest() {
        for (i in 0..5000) {
            launchHistoryList.add(i.toString(), ArrayRealVector(allInstalledApps.size))
        }
        Log.d("test-sizeTestB4", launchHistoryList.size.toString())
        // JUtils.dropFirstKey(launchHistory)  // wrapper Java method to avoid "unresolved overload ambiguity"
        launchHistoryList.removeAt(0)
        Log.d("test-sizeTestAfter", launchHistoryList.size.toString())
    }

    fun saveState() {
        GlobalScope.launch {
            saveLaunchSequence()
            saveLaunchHistory()
            savePreds()

            LogHelper.getLogHelper(context).addLogToQueue(
                "saveState called: " +
                        "launchSeq=${launchSequence.size}" +
                        ", histSize=${launchHistoryList.size}, preds=${appSuggestions.size}",
                LogHelper.LOG_LEVEL.INFO, "SLRoot"
            )
        }
    }

    fun loadState() {
        GlobalScope.launch {
            loadLaunchSequence()
            loadLaunchHistory()
            loadPreds()

            LogHelper.getLogHelper(context).addLogToQueue(
                "loadState called: " +
                        "launchSeq=${launchSequence.size}" +
                        ", histSize=${launchHistoryList.size}, preds=${appSuggestions.size}",
                LogHelper.LOG_LEVEL.INFO, "SLRoot"
            )
        }
    }

    suspend fun saveLaunchSequence() {
        val fileLaunchSeq = File(
            Utils.getAppFolderInternal(context),
            Constants.LAUNCH_SEQUENCE_SAVE_FILE
        )
        Utils.writeToFile(context, fileLaunchSeq.absolutePath, launchSequence as Object)
    }

    suspend fun saveLaunchHistory() {
        val file = File(
            Utils.getAppFolderInternal(context),
            Constants.LAUNCH_HISTORY_SAVE_FILE
        )
        Utils.writeToFile(context, file.absolutePath, launchHistoryList as Object)
        // launcherPref.edit().putString(Constants.LAUNCH_HISTORY_SAVE_FILE, Gson().toJson(launchHistory)).apply()
        Log.v("test-launchHistorySave", Gson().toJson(launchHistoryList))
    }

    suspend fun savePreds() {
        val file = File(
            Utils.getAppFolderInternal(context),
            Constants.APP_SUGGESTIONS_SAVE_FILE
        )
        val temp: List<String> = appSuggestions.map { it.packageName }
        Utils.writeToFile(context, file.absolutePath, temp as Object)
    }

    suspend fun loadLaunchSequence() {
        val fileLaunchSeq = File(
            Utils.getAppFolderInternal(context),
            Constants.LAUNCH_SEQUENCE_SAVE_FILE
        )
        val temp = Utils.readFromFile<ArrayList<String>>(context, fileLaunchSeq.absolutePath)
        if (temp != null)
            launchSequence = temp
    }

    suspend fun loadLaunchHistory() {
        val file = File(
            Utils.getAppFolderInternal(context),
            Constants.LAUNCH_HISTORY_SAVE_FILE
        )
        val checkPoint = Utils.readFromFileAsString(context, file.absolutePath)
        //{"com.google.android.calendar":{"data":[0.41935483870967744,0.041666666666666664,0.03597791333333333,0.21588130694444443,0.0,1.0,0.0,0.0,1.0,0.0,0.97,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]},"com.google.android.deskclock":{"data":[0.41935483870967744,0.041666666666666664,0.03597791861111111,0.2158812911111111,0.0,1.0,0.0,0.0,1.0,0.0,0.97,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}}
        // [{"key":"0","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"1","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"2","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"3","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"4","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"5","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"6","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"7","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"8","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}},{"key":"9","value":{"data":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}}]
        // val checkPoint = launcherPref.getString(Constants.LAUNCH_HISTORY_SAVE_FILE, "")
        Log.v("test-lhLoad", "checkpoint= $checkPoint")
        if (checkPoint != null && checkPoint.isNotEmpty()) {
            try {
                val temp = JSONArray(checkPoint)
                launchHistoryList.clear()
                for(i in 0 until temp.length()){
                    val tupleJObj = temp.getJSONObject(i)
                    val key = tupleJObj.getString("key")
                    val value = tupleJObj.getJSONObject("value").getJSONArray("data")
                    val vec = ArrayRealVector(value.length())
                    for(j in 0 until value.length()){
                        vec.setEntry(j, value.getDouble(j))
                    }
                    launchHistoryList.add(key, vec)
                }
                Log.v("test-launchHistoryLoad", "${temp.length()}, ${launchHistoryList.size}")
                cleanUpHistory()  // TODO review if this is correct place to perform clean up or should it be scheduled
            } catch (ex: JSONException) {
                Log.e("test-lhJSON", Log.getStackTraceString(ex))
                FirebaseCrashlytics.getInstance().recordException(ex)
            }
        }
        if (launchHistoryList.size > 0)
            Log.v("test-launchHistoryLoad", "$launchHistoryList")
    }

    suspend fun loadPreds() {
        val file = File(
            Utils.getAppFolderInternal(context),
            Constants.APP_SUGGESTIONS_SAVE_FILE
        )
        val temp = Utils.readFromFile<ArrayList<String>>(context, file.absolutePath)
        appSuggestions.clear()
        if (temp != null) {
            for (packageName in temp) {
                for (appModel in allInstalledApps) {
                    if (appModel.packageName.equals(packageName, true))
                        appSuggestions.add(appModel)
                }
            }
        }
        appSuggestionsLiveData.postValue(appSuggestions)
    }

    suspend fun cleanUpHistory() {
        Log.v("test-cleanUpHist", "${launchHistoryList.size}, $HISTORY_MAX_SIZE")
        if (launchHistoryList.size > HISTORY_MAX_SIZE) {
            Log.v("test-hist", "$launchHistoryList")
            withContext(Dispatchers.IO) {
                val topHalf: Int = launchHistoryList.size / 2
                var counterMap = ArrayMap<String, Int>() // package to frequency map
                for (i in 0 until topHalf) {  // look into the top oldest records only
                    val packageName = launchHistoryList[i].key
                    counterMap[packageName] = counterMap.getOrDefault(packageName, 0) + 1
                }
                /*counterMap = counterMap.entries.sortedBy { -it.value }
                    .associate { it.toPair() } as ArrayMap<String, Int>*/
                val counterMap1 = counterMap.toList().sortedBy { (key, value) -> value }.toMap()
                Log.v("test-counter", "$counterMap1, threshold=${0.01* HISTORY_MAX_SIZE}")
                for((packageName, frequency) in counterMap1){
                    if(frequency < 0.01* HISTORY_MAX_SIZE){ // frequency less that 1% of MAX size
                        launchHistoryList.removeEntriesWithKey(packageName)
                        Log.v("test-cleanUpHistory", "deleted package: $packageName with $frequency launches")
                    }
                }
            }
        }
    }
}