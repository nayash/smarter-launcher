package com.outliers.smartlauncher.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.collection.ArrayMap
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.consts.Constants
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.models.AppModel.CREATOR.getAppModelsFromPackageInfoList
import com.outliers.smartlauncher.utils.JUtils
import com.outliers.smartlauncher.utils.LogHelper
import com.outliers.smartlauncher.utils.Utils
import com.outliers.smartlauncher.utils.Utils.isValidString
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.collections4.map.LinkedMap
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.pow

class SmartLauncherRoot private constructor(val context: Context) {

    val appModels: ArrayList<AppModel> = ArrayList()
    val appToIdMap: ArrayMap<String, Int> = ArrayMap()  // package to hashcode map
    val appToIdxMap: ArrayMap<String, Int> = ArrayMap()  // package to index map for ATF construction
    val idToApp: ArrayMap<Int, String> = ArrayMap()  // reverse Map of appToIdMap
    var launchSequence: ArrayList<String> = ArrayList(WINDOW_SIZE)  // sequence of last 'window size' package names
    var launchHistory: LinkedMap<String, ArrayRealVector> = LinkedMap()  //
    val launcherPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    var appSuggestions = ArrayList<AppModel>(APP_SUGGESTION_COUNT)
    val appSuggestionsLiveData: MutableLiveData<ArrayList<AppModel>> = MutableLiveData()
    val logHelper = LogHelper.getLogHelper(context)

    companion object {
        private var outliersLauncherRoot: SmartLauncherRoot? = null  // TODO warning--context in static field; memory leak.
        fun getInstance(context: Context): SmartLauncherRoot? {
            if (outliersLauncherRoot == null) {
                Log.e("test-slRoot", "calling slRoot constructor")
                outliersLauncherRoot = SmartLauncherRoot(context)
            }
            return outliersLauncherRoot
        }
        const val WINDOW_SIZE = 3
        const val APP_SUGGESTION_COUNT = 8
        const val EXPLICIT_FEATURES_COUNT = 11
        const val APP_USAGE_DECAY_RATE = 0.5
        const val EPSILON = 0.1
    }

    init {
        allInstalledApps
        initPackageToIdMap()
        // sizeTest()
        loadState()
    }

    val allInstalledApps: ArrayList<AppModel>
        get() {  // TODO instead of querying installed apps all the time, implement install/uninstall listeners and update only in such events?
            if (appModels.size == 0) {
                //allInstalledApps.clear()
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

    fun initPackageToIdMap(){
        for((idx: Int, app: AppModel) in appModels.withIndex()){
            appToIdMap[app.packageName] = app.packageName.hashCode()
            appToIdxMap[app.packageName] = idx
            idToApp[appToIdMap[app.packageName]] = app.packageName
        }
    }

    fun appLaunched(packageName: String){
        GlobalScope.launch {
            Log.v("test-lseq", "calling processAppSuggestion")
            processAppSuggestion(packageName)
            Log.v("test-lseq", "called processAppSuggestion")
            if(launchSequence.size >=3)
                launchSequence.removeAt(0)  // remove oldest app history
            launchSequence.add(packageName)
            Log.v("test-lSeq", launchSequence.toString())
        }
    }

    suspend fun processAppSuggestion(packageName: String){
        withContext(Dispatchers.IO) {
            val stime = System.currentTimeMillis()
            val launchVec = genAppLaunchVec(packageName)
            // TODO find KNN
            appSuggestions.clear()
            appSuggestions.addAll(findKNN(launchVec))
            notifyNewSuggestions()
            // once all calculations and prediction is done, store the launchVec as history,
            // to use it for future predictions
            if(appToIdMap[packageName] == null)
                Log.e("test-packageNull", "$packageName --- ${appToIdxMap.size}, ${allInstalledApps.size}")
            // appToIdMap[packageName]?.let { launchHistory.put(it, launchVec) }
            launchHistory.put(packageName, launchVec)
            Log.v("test-launchHistorySize", launchHistory.size.toString())
            val duration = (System.currentTimeMillis()-stime)/1000
            Log.d("test-processTime", "processing duration = $duration secs")
        }
    }

    private suspend fun findKNN(launchVec: ArrayRealVector): ArrayList<AppModel>{
        val appPreds = ArrayList<AppModel>()
        var appScoresMap = HashMap<String, Double>()  // appPackage to score mapping, to later apply toSrotedMap
        for((packageName, lVecHist) in launchHistory){
            val distance = lVecHist.getDistance(launchVec)
            val similarity = 1/(distance + EPSILON)

            // appToIdxMap[idToApp[appId]]?.let { appScoresMap[it] += similarity }
            /*idToApp[appId]?.let{ packageName->
                var prevScore = appScoresMap[packageName] ?: 0.0
                prevScore += similarity
                appScoresMap[packageName] = prevScore
            }*/
            var prevScore = appScoresMap[packageName] ?: 0.0
            prevScore += similarity
            appScoresMap[packageName] = prevScore
        }
        var breaker = 0
        Log.v("test-appScores", appScoresMap.toString())
        appScoresMap = appScoresMap.entries.sortedBy { -it.value }.associate { it.toPair() } as HashMap<String, Double>
        Log.v("test-appScoreSorted", appScoresMap.toString())
        for((packageName, score) in appScoresMap){ // TODO IMP!!!! this is wrong, sort by value not key
            Utils.getAppByPackage(allInstalledApps, packageName)?.let { appPreds.add(it) }
            breaker++
            Log.v("test-app-predictions", "$packageName-->$score")
            if(breaker > APP_SUGGESTION_COUNT)
                break
        }
        return appPreds
    }

    private suspend fun genAppLaunchVec(packageName: String): ArrayRealVector{
        /**
         * DayOfMonth (not in paper), Time (hourOfDay), location, weekend, AM, BTheadset, wiredHeadset, charging,
         * cellularDataActive, wifiConnected, battery, ATF
         */
        // TODO this size will change when apps are installed or uninstalled. Need to handle such cases: one possible
        // approach is to remove the appIdx from all launch vectors (for uninstall) and for new installations, add app to bottom of array

        val vecSize = EXPLICIT_FEATURES_COUNT + allInstalledApps.size
        val launchVec = ArrayRealVector(vecSize)
        Log.v("test-genLVec", launchVec.dimension.toString())
        var featureIdx = 0
        launchVec.setEntry(featureIdx++, Utils.getDayOfMonth() / 31.0)
        val hourOfDay = Utils.getHourOfDay()
        launchVec.setEntry(featureIdx++, hourOfDay / 24.0)
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            val channel = Channel<Int>()
            // TODO add code to check quality of last known location; else request fresh location
            /*fusedLocationClient.lastLocation.addOnSuccessListener { location->
                launchVec.setEntry(featureIdx++, location.latitude/360.0)
                launchVec.setEntry(featureIdx++, location.longitude/360.0)
                Log.v("test-location", "offering 0")
                channel.offer(0)
            }*/
            if(Utils.isLocationEnabled(context)) {
                Log.v("test-location", "getting loc")
                fusedLocationClient.getCurrentLocation(
                    LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                )
                    .addOnSuccessListener { location ->
                        Log.d("test-location", location?.toString() + ",")
                        location?.let {
                            launchVec.setEntry(featureIdx++, location.latitude / 360.0)
                            launchVec.setEntry(featureIdx++, location.longitude / 360.0)
                            Log.v("test-location", "offering 0")
                        }
                        channel.offer(0)
                    }
                channel.receive()
            }else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.location_disabled),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        Log.v("test-location", "adding new features")
        val isWeekend: Double = (if (Utils.isTodayWeekend()) 1.0 else 0.0)
        launchVec.setEntry(featureIdx++, isWeekend)
        val isAM: Double = if(Utils.isHourAM(Utils.getHourOfDay())) 1.0 else 0.0
        launchVec.setEntry(featureIdx++, isAM)
        val isBTHeadset: Double = if(Utils.isBluetoothHeadsetConnected()) 1.0 else 0.0
        launchVec.setEntry(featureIdx++, isBTHeadset)
        val isWiredHeadset: Double = if(Utils.isWiredHeadsetConnected(context)) 1.0 else 0.0
        launchVec.setEntry(featureIdx++, isWiredHeadset)
        val isChargin: Double = if(Utils.isCharging(context)) 1.0 else 0.0
        launchVec.setEntry(featureIdx++, isChargin)
        val isMobileData: Double = if(Utils.isMobileDataConnected(context)) 1.0 else 0.0
        launchVec.setEntry(featureIdx++, isMobileData)
        val isWifi: Double = if(Utils.isWifiConnected(context)) 1.0 else 0.0
        launchVec.setEntry(featureIdx++, isWifi)
        Utils.getBatteryLevel(context)?.div(100.0)?.let { launchVec.setEntry(10, it) }

        /* // won't be same if some features are not allowed-- e.g. location settings off or permission not granted
        if(featureIdx-1 != EXPLICIT_FEATURES_COUNT)
            throw Exception("Wrong feature construction: $featureIdx, $EXPLICIT_FEATURES_COUNT")*/

        for((i, appPackage) in launchSequence.asReversed().withIndex()){
            /**
             * if app launch history for window=3 is (oldest to latest) [instagram, facebook, Maps] then this loop
             * iterates in reversed order (latest to oldest) and calculates ATF values and set to corresponding app index
             */
            val appIdx = appToIdxMap[appPackage]
            val appValue = APP_USAGE_DECAY_RATE.pow(i)
            // TODO convert this to nullable expression. Package should be availabe in hashmap if not it should crash
            if(appIdx == null)
                Log.e("test-packageNull", "$appPackage --- ${appToIdxMap.size}, ${allInstalledApps.size}")
            launchVec.setEntry(EXPLICIT_FEATURES_COUNT + appIdx!!, appValue)  // TODO IMP!!! crash here: DefaultDispatcher-worker-3 Process: com.outliers.smartlauncher, PID: 15033 java.lang.NullPointerException
            Log.d("test-ATF-val", "$packageName, $appIdx, $EXPLICIT_FEATURES_COUNT, $appValue")
        }
        Log.v("test-appToIdx", appToIdxMap.toString())
        Log.v("test-lvec", launchVec.toString())

        return launchVec
    }

    fun notifyNewSuggestions(){
        appSuggestionsLiveData.postValue(appSuggestions)
    }

    fun refreshAppList(){
        appModels.clear()
        allInstalledApps
    }

    fun sizeTest(){
        for(i in 0..5000){
            launchHistory.put(i.toString(), ArrayRealVector(allInstalledApps.size))
        }
        Log.d("test-sizeTestB4", launchHistory.size.toString())
        // JUtils.dropFirstKey(launchHistory)  // wrapper Java method to avoid "unresolved overload ambiguity"
        launchHistory.remove(0)
        Log.d("test-sizeTestAfter", launchHistory.size.toString())
    }

    fun saveState(){
        GlobalScope.launch {
            saveLaunchSequence()
            saveLaunchHistory()
            savePreds()

            LogHelper.getLogHelper(context).addLogToQueue("saveState called: " +
                    "launchSeq=${launchSequence.size}" +
                    ", histSize=${launchHistory.size}, preds={$appSuggestions.size}",
                LogHelper.LOG_LEVEL.INFO, "SLRoot")
        }
    }

    fun loadState(){
        GlobalScope.launch {
            loadLaunchSequence()
            loadLaunchHistory()
            loadPreds()

            LogHelper.getLogHelper(context).addLogToQueue("loadState called: " +
                    "launchSeq=${launchSequence.size}" +
                    ", histSize=${launchHistory.size}, preds={$appSuggestions.size}",
                LogHelper.LOG_LEVEL.INFO, "SLRoot")
        }
    }

    suspend fun saveLaunchSequence(){
        val fileLaunchSeq = File(Utils.getAppFolderInternal(context),
            Constants.LAUNCH_SEQUENCE_SAVE_FILE)
        Utils.writeToFile(context, fileLaunchSeq.absolutePath, launchSequence as Object)
    }

    suspend fun saveLaunchHistory(){
        val file = File(Utils.getAppFolderInternal(context),
            Constants.LAUNCH_HISTORY_SAVE_FILE)
        Utils.writeToFile(context, file.absolutePath, launchHistory as Object)
        // launcherPref.edit().putString(Constants.LAUNCH_HISTORY_SAVE_FILE, Gson().toJson(launchHistory)).apply()
        Log.v("test-launchHistorySave", Gson().toJson(launchHistory))
    }

    suspend fun savePreds(){
        val file = File(Utils.getAppFolderInternal(context),
            Constants.APP_SUGGESTIONS_SAVE_FILE)
        val temp: List<String> = appSuggestions.map { it.packageName }
        Utils.writeToFile(context, file.absolutePath, temp as Object)
    }

    suspend fun loadLaunchSequence(){
        val fileLaunchSeq = File(Utils.getAppFolderInternal(context),
            Constants.LAUNCH_SEQUENCE_SAVE_FILE)
        val temp = Utils.readFromFile<ArrayList<String>>(context, fileLaunchSeq.absolutePath)
        if(temp != null)
            launchSequence =  temp
    }

    suspend fun loadLaunchHistory(){
        val file = File(Utils.getAppFolderInternal(context),
            Constants.LAUNCH_HISTORY_SAVE_FILE)
        val checkPoint = Utils.readFromFileAsString(context, file.absolutePath)
        //{"com.google.android.calendar":{"data":[0.41935483870967744,0.041666666666666664,0.03597791333333333,0.21588130694444443,0.0,1.0,0.0,0.0,1.0,0.0,0.97,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]},"com.google.android.deskclock":{"data":[0.41935483870967744,0.041666666666666664,0.03597791861111111,0.2158812911111111,0.0,1.0,0.0,0.0,1.0,0.0,0.97,0.0,0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]}}
        // val checkPoint = launcherPref.getString(Constants.LAUNCH_HISTORY_SAVE_FILE, "")
        Log.v("test-lhLoad", "checkpoint= $checkPoint")
        if(checkPoint != null && checkPoint.isNotEmpty()){
            try {
                val temp = JSONObject(checkPoint)
                launchHistory.clear()
                for(key:String in temp.keys()){
                    val vecJArray = temp.getJSONObject(key).getJSONArray("data")
                    val realVec = ArrayRealVector(vecJArray.length())
                    for(i in 0..vecJArray.length()-1){
                        realVec.setEntry(i, vecJArray.getDouble(i))
                    }
                    launchHistory.set(key, realVec)
                }
                Log.v("test-launchHistoryLoad", "$temp")
            }catch (ex: JSONException){
                Log.e("test-lhJSON", Log.getStackTraceString(ex))
            }
        }
        if(launchHistory.size > 0)
            Log.v("test-launchHistoryLoad", "$launchHistory")
    }

    suspend fun loadPreds(){
        val file = File(Utils.getAppFolderInternal(context),
            Constants.APP_SUGGESTIONS_SAVE_FILE)
        val temp = Utils.readFromFile<ArrayList<String>>(context, file.absolutePath)
        appSuggestions.clear()
        if(temp != null) {
            for(packageName in temp) {
                for(appModel in allInstalledApps) {
                    if(appModel.packageName.equals(packageName, true))
                    appSuggestions.add(appModel)
                }
            }
        }
        appSuggestionsLiveData.postValue(appSuggestions)
    }
}