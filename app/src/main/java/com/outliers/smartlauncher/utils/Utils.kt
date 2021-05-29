/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.utils

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.gson.Gson
import com.outliers.smartlauncher.BuildConfig
import com.outliers.smartlauncher.debugtools.loghelper.LogHelper
import com.outliers.smartlauncher.models.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.math3.linear.RealVector
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


object Utils {
    @JvmStatic
    fun getApplicationName(context: Context, appInfo: ApplicationInfo): String {
        val stringId = appInfo.labelRes
        val name: String
        name = if (stringId == 0) {
            if (appInfo.nonLocalizedLabel != null) appInfo.nonLocalizedLabel.toString() else ""
        } else {
            context.packageManager.getApplicationLabel(appInfo).toString()
        }
        return name
    }

    @JvmStatic
    fun isValidString(text: String?): Boolean {
        return text != null && !text.trim { it <= ' ' }.isEmpty()
    }

    fun rotateView(view: View, from: Float, to: Float, duration: Long = 200) {
        // not working as expected. needs work.
        val rotateAnim = RotateAnimation(0f, 180f, 0.5f, 0.5f)
        rotateAnim.duration = duration
        rotateAnim.interpolator = AccelerateDecelerateInterpolator()
        rotateAnim.fillAfter = true
        view.startAnimation(rotateAnim)
    }

    fun hideKeyboard(activity: Activity) {
        val imm: InputMethodManager =
            activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        var view = activity.currentFocus
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = View(activity)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun isTodayWeekend(): Boolean {
        val c = Calendar.getInstance()
        return c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    }

    fun isHourAM(hourOfDay: Int): Boolean {
        return hourOfDay < 12
    }

    fun getDayOfMonth(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    }

    fun getHourOfDay(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    fun isBluetoothHeadsetConnected(): Boolean {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled
                && (mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED
                || mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.A2DP) == BluetoothHeadset.STATE_CONNECTED))
    } //BluetoothHeadset.A2DP can also be used for Stereo media devices.

    fun isWiredHeadsetConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        return audioManager!!.isWiredHeadsetOn
    }

    fun isHeadsetConnected(context: Context): Boolean { // either BT or wired
        return isBluetoothHeadsetConnected() || isWiredHeadsetConnected(context)
    }

    fun l2Distance(vec1: RealVector, vec2: RealVector): Double {
        return vec1.getDistance(vec2)
    }

    fun isCharging(context: Context): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        // isCharging if true indicates charging is ongoing and vice-versa
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
        return isCharging
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        val ni = cm!!.activeNetworkInfo
        if (ni != null && ni.type == ConnectivityManager.TYPE_WIFI) {
            return true
        }
        return false
    }

    fun isMobileDataConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        val ni = cm!!.activeNetworkInfo
        if (ni != null && ni.type == ConnectivityManager.TYPE_MOBILE) {
            return true
        }
        return false
    }

    fun getConnectionType(context: Context): Int {
        /** Needs more work -- for some reason it gives wrong result on Moto G5 s plus
         * checks and returns the data connection type:
         * 0 - no data connection
         * 1 - WiFi
         * 2 - Mobile data
         */
        var result = 0
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return 0
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return 0
            result = when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return 1
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> return 2
                // actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> 0
            }
        } else {
            connectivityManager.run {
                connectivityManager.activeNetworkInfo?.run {
                    result = when (type) {
                        ConnectivityManager.TYPE_WIFI -> 1
                        ConnectivityManager.TYPE_MOBILE -> 2
                        // ConnectivityManager.TYPE_ETHERNET -> true
                        else -> 0
                    }
                }
            }
        }

        return result
    }

    fun getBatteryLevel(context: Context): Float? {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }
        return batteryPct
    }

    fun showAlertDialog(
        context: Context, title: String, message: String,
        positiveCallback: () -> Unit,
        negativeCallback: () -> Unit,
        positiveAction: String = context.getString(android.R.string.ok),
        negativeAction: String = context.getString(android.R.string.cancel)
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(positiveAction) { _: DialogInterface, i: Int ->
            positiveCallback()
        }

        builder.setNegativeButton(negativeAction) { dialogInterface: DialogInterface, i: Int ->
            negativeCallback()
            dialogInterface.dismiss()
        }
        builder.show()
    }

    fun isLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) {
            var locationMode = 0
            val locationProviders: String
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    locationMode =
                        Settings.Secure.getInt(
                            context.contentResolver,
                            Settings.Secure.LOCATION_MODE
                        )
                } catch (e: Settings.SettingNotFoundException) {
                    e.printStackTrace()
                }
                locationMode != Settings.Secure.LOCATION_MODE_OFF
            } else {
                locationProviders = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED
                )
                !TextUtils.isEmpty(locationProviders)
            }
        } else {
            val locationManager: LocationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.isLocationEnabled
        }
    }

    fun convertDpToPixel(dp: Float): Float {
        return dp * (Resources.getSystem().displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    fun convertPixelsToDp(px: Float): Float {
        return px / (Resources.getSystem().displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }

    fun getAppByPackage(appsList: ArrayList<AppModel>, packageName: String): AppModel? {
        for (appModel in appsList) {
            if (appModel.packageName.equals(packageName, true))
                return appModel
        }
        return null
    }

    @JvmStatic
    fun getAppFolderInternal(context: Context): File {
        val file = File(context.filesDir, "smart_launcher")
        if (!file.exists()) {
            file.mkdir()
        }
        return file
    }

    @JvmStatic
    fun getAppDataFolderInternal(context: Context): File {
        val file = File(getAppFolderInternal(context), "data")
        if (!file.exists()) {
            file.mkdir()
        }
        return file
    }

    @JvmStatic
    fun getAppLogFolderInternal(context: Context): File {
        val file = File(getAppFolderInternal(context), "logs")
        if (!file.exists()) {
            file.mkdir()
        }
        return file
    }

    @JvmStatic
    fun getDate(timeInMilli: Long, format: String?): String? {
        return if (timeInMilli != 0L) {
            val sdf = SimpleDateFormat(format ?: "dd/MM/yyyy")
            var date: String? = null
            val now = Date(timeInMilli)
            date = sdf.format(now)
            date
        } else {
            "0"
        }
    }

    @JvmStatic
    fun getLibVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    @JvmStatic
    fun getDeviceDetails(): JSONObject {
        val device = JSONObject()
        try {
            device.put("device", Build.MANUFACTURER + "_" + Build.MODEL)
            device.put("version", Build.VERSION.SDK_INT)
        } catch (ex: java.lang.Exception) {
            try {
                device.put("device", ex.message)
            } catch (e: java.lang.Exception) {
            }
        }
        return device
    }

    suspend fun writeToFile(context: Context, path: String, objectToWrite: Any): Boolean {
        try {
            Log.v("writeToFile", "obj = $objectToWrite")
            withContext(Dispatchers.IO) {
                val serializedObj =
                    Gson().toJson(objectToWrite)  // TODO handle concurrent modification exception
                val fos: FileOutputStream = FileOutputStream(path)
                val os = ObjectOutputStream(fos)
                os.writeObject(serializedObj)
                os.close()
                fos.close()
            }
            return true
        } catch (ex: Exception) {
            LogHelper.getLogHelper(context)?.addLogToQueue(
                "writeToFileException:" +
                        Log.getStackTraceString(ex), LogHelper.LOG_LEVEL.ERROR, context
            )
            return false
        }
    }

    suspend inline fun <reified T> readFromFile(context: Context, fileName: String): T? {
        var obj: T? = null
        try {
            withContext(Dispatchers.IO) {
                val temp = readFromFileAsString(context, fileName)
                obj = Gson().fromJson(temp, T::class.java)
            }
        } catch (ex: Exception) {
            LogHelper.getLogHelper(context)?.addLogToQueue(
                "readFromFileException1:" +
                        "${Log.getStackTraceString(ex)}\nobj=${obj.toString()}",
                LogHelper.LOG_LEVEL.ERROR,
                context
            )
            obj = null
            FirebaseCrashlytics.getInstance().recordException(ex)
        }
        return obj
    }

    suspend fun readFromFileAsString(context: Context, fileName: String): String? {
        val sb = StringBuilder()
        try {
            withContext(Dispatchers.IO) {
                val br = BufferedReader(FileReader(File(fileName)))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line)
                    sb.append('\n')
                }
                br.close()
            }
        } catch (ex: Exception) {
            LogHelper.getLogHelper(context)?.addLogToQueue(
                "readFromFileException2:" +
                        "${Log.getStackTraceString(ex)}\nobj=${sb.toString()}",
                LogHelper.LOG_LEVEL.ERROR,
                context
            )
            FirebaseCrashlytics.getInstance().recordException(ex)
        }
        return sb.toString()
    }

    suspend fun readFromFileAsString(context: Context, fileName: Uri): String? {
        val sb = StringBuilder()
        try {
            withContext(Dispatchers.IO) {
                val br = BufferedReader(FileReader(File(fileName.toString())))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    sb.append(line)
                    sb.append('\n')
                }
                br.close()
            }
        } catch (ex: Exception) {
            LogHelper.getLogHelper(context)?.addLogToQueue(
                "readFromFileException2:" +
                        "${Log.getStackTraceString(ex)}\nobj=${sb.toString()}",
                LogHelper.LOG_LEVEL.ERROR,
                context
            )
            FirebaseCrashlytics.getInstance().recordException(ex)
        }
        return sb.toString()
    }

    fun isMyAppLauncherDefault(context: Context): Boolean {
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        val filters: MutableList<IntentFilter> = ArrayList()
        filters.add(filter)
        val myPackageName: String = context.packageName
        val activities: List<ComponentName> = ArrayList()
        val packageManager = context.packageManager
        packageManager.getPreferredActivities(filters, activities, null)
        for (activity in activities) {
            if (myPackageName == activity.packageName) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun bytesToKB(bytes: Long): Long {
        return bytes / 1024
    }

    fun getFileNameFromPath(path: String): String {
        return path.split(File.separator).last()
    }
}