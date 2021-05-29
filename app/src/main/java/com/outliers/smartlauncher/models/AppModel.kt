/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.models

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.outliers.smartlauncher.utils.Utils.getApplicationName

data class AppModel(
    var appName: String, var packageName: String,
    var mainActivityName: String, var versionName: String,
    var appIconId: Int, var versionCode: Int,
    var appIcon: Drawable? = null, var launchIntent: Intent? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(), parcel.readInt(),
        parcel.readParcelable(Intent::class.java.classLoader)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(appName)
        parcel.writeString(packageName)
        parcel.writeString(mainActivityName)
        parcel.writeString(versionName)
        parcel.writeInt(appIconId)
        parcel.writeInt(versionCode)
        parcel.writeParcelable(launchIntent, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppModel> {
        override fun createFromParcel(parcel: Parcel): AppModel {
            return AppModel(parcel)
        }

        override fun newArray(size: Int): Array<AppModel?> {
            return arrayOfNulls(size)
        }

        @JvmStatic
        fun getAppModelsFromAppInfoList(
            applicationInfos: List<ApplicationInfo>,
            context: Context
        ): ArrayList<AppModel> {
            val appModels = ArrayList<AppModel>()
            for (applicationInfo in applicationInfos) {
                val appModel = AppModel(
                    "", "",
                    "", "",
                    0, 0, null, null
                )
                appModel.appIconId = applicationInfo.icon
                appModel.appName = getApplicationName(context, applicationInfo)
                appModel.packageName = applicationInfo.packageName
                try {
                    val componentName =
                        context.packageManager.getLaunchIntentForPackage(applicationInfo.packageName)!!.component
                    appModel.mainActivityName = componentName!!.className
                } catch (ex: NullPointerException) {
                }
                try {
                    val packageInfo = context.packageManager.getPackageInfo(
                        appModel.packageName, 0
                    )
                    appModel.versionCode =
                        packageInfo.versionCode // TODO use longVersionCode instead
                    appModel.versionName = packageInfo.versionName //
                } catch (ex: Exception) {
                }
                appModels.add(appModel)
            }
            return appModels
        }

        @JvmStatic
        fun getAppModelsFromPackageInfoList(
            packageInfos: List<PackageInfo>,
            appInfos: List<ApplicationInfo>,
            context: Context
        ): ArrayList<AppModel> {
            val appModels = ArrayList<AppModel>()
            /*val userFacingApps = HashMap<String, Intent?>()

            for (appInfo: ApplicationInfo in appInfos) {
                if (context.packageManager.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    // getLaunchIntent for package. if not null then user-facing
                    userFacingApps.put(
                        appInfo.packageName,
                        context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    )
                }
            }*/
            // Log.v("test", userFacingApps.size.toString())
            for (p in packageInfos) {
                try {
                    val appModel = AppModel(
                        "", "",
                        "", "",
                        0, 0, null, null
                    )
                    appModel.appName =
                        p.applicationInfo.loadLabel(context.packageManager).toString()
                    appModel.packageName = p.packageName
                    // Log.v("testVname", p.packageName)
                    appModel.versionName = p.versionName
                    appModel.versionCode = p.versionCode  // TODO use longVersionCode instead
                    appModel.appIcon = p.applicationInfo.loadIcon(context.packageManager)
                    appModel.launchIntent =
                        context.packageManager.getLaunchIntentForPackage(p.packageName)
                    // if(userFacingApps.contains(appModel.packageName))
                    if (appModel.launchIntent != null)
                        appModels.add(appModel)
                } catch (ex: NullPointerException) {
                    Log.e("appModelError", Log.getStackTraceString(ex) + "\n" + p.packageName)
                }
            }
            return appModels
        }

        fun getRandomApps(size: Int = 5): ArrayList<AppModel> {
            val appModels = ArrayList<AppModel>()
            for (i in 0 until size) {
                val packageName = "com.test.package$i"
                val appName = "AppNum$i"
                val versionName = "1.0"
                val versionCode = 9876
                val appIcon = null
                val launchIntent = null
                val appModel = AppModel(
                    appName, packageName,
                    "", versionName,
                    0, versionCode, appIcon, launchIntent
                )
                appModels.add(appModel)
            }
            return appModels
        }
    }
}