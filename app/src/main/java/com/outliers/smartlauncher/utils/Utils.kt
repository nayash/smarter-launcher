package com.outliers.smartlauncher.utils

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.RotateAnimation
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat.getSystemService
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
}