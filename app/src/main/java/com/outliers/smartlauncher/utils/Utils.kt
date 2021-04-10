package com.outliers.smartlauncher.utils

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import org.apache.commons.math3.linear.RealVector
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

    fun rotateView(view: View, from: Float, to: Float, duration: Long = 200){
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

    fun isTodayWeekend(): Boolean{
        val c = Calendar.getInstance()
        return c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    }

    fun isHourAM(hourOfDay: Int): Boolean{
        return hourOfDay < 12
    }

    fun getDayOfMonth(): Int{
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    }

    fun getHourOfDay(): Int{
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

    fun isHeadsetConnected(context: Context): Boolean{ // either BT or wired
        return isBluetoothHeadsetConnected() || isWiredHeadsetConnected(context)
    }

    fun l2Distance(vec1: RealVector, vec2: RealVector): Double{
        return vec1.getDistance(vec2)
    }

    fun isCharging(context: Context): Boolean{
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        // isCharging if true indicates charging is ongoing and vice-versa
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
        return isCharging
    }

    fun isWifiConnected(context: Context): Boolean{
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        val ni = cm!!.activeNetworkInfo
        if (ni != null && ni.type == ConnectivityManager.TYPE_WIFI) {
            return true
        }
        return false
    }

    fun isMobileDataConnected(context: Context): Boolean{
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

    fun getBatteryLevel(context: Context): Float?{
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
    ){
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
        if(Build.VERSION.SDK_INT < 28) {
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
        }else {
            val locationManager: LocationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.isLocationEnabled
        }
    }
}