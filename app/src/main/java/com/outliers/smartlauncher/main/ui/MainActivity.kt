/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.main.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.outliers.smartlauncher.BuildConfig
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.core.SmartLauncherApplication
import com.outliers.smartlauncher.databinding.ActivityMainBinding
import com.outliers.smartlauncher.debugtools.backup.BackupActivity
import com.outliers.smartlauncher.debugtools.loghelper.LogsActivity
import com.outliers.smartlauncher.main.adapter.AppsRVAdapter
import com.outliers.smartlauncher.main.adapter.RVItemDecoration
import com.outliers.smartlauncher.main.viewmodel.MainViewModel
import com.outliers.smartlauncher.main.viewmodel.MainViewModelFactory
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.utils.Utils
import java.util.*


class MainActivity : AppCompatActivity(), AppsRVAdapter.IAppsRVAdapter, View.OnClickListener,
    MainViewModel.MainVMParent {

    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    lateinit var viewModel: MainViewModel
    var adapter: AppsRVAdapter? = null
    val sheetBehavior by lazy { BottomSheetBehavior.from(binding.appListSheet) }
    val appPredViewGroup by lazy { binding.rlPredApps }
    lateinit var etSearch: EditText
    var appPredAdapter: AppsRVAdapter? = null

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val vmFactory = MainViewModelFactory(application as SmartLauncherApplication, this)
        viewModel = ViewModelProviders.of(this, vmFactory).get(MainViewModel::class.java)

        etSearch = binding.appListSheet.findViewById(R.id.et_search)
        val ivSearchSearch = binding.appListSheet.findViewById<ImageView>(R.id.iv_right_search)
        val ivSearchClose = binding.appListSheet.findViewById<ImageView>(R.id.iv_right_cross)
        ivSearchClose.setOnClickListener(this)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotEmpty()) {
                    // show cross
                    ivSearchSearch.visibility = View.GONE
                    ivSearchClose.visibility = View.VISIBLE
                } else {
                    // search icon
                    ivSearchSearch.visibility = View.VISIBLE
                    ivSearchClose.visibility = View.GONE
                }
                searchApp(s.toString())
            }
        })

        val imageView = binding.appListSheet.findViewById<ImageView>(R.id.iv_expand)
        val rvApps = binding.appListSheet.findViewById<RecyclerView>(R.id.rv_apps)
        adapter = AppsRVAdapter(viewModel.appList, this, this)
        val appsPerRow = resources.getInteger(R.integer.app_per_row)
        rvApps.layoutManager = GridLayoutManager(this, appsPerRow)
        rvApps.addItemDecoration(
            RVItemDecoration(
                appsPerRow,
                getResources().getDimensionPixelSize(R.dimen.margin_default), true
            )
        )
        rvApps.adapter = adapter
        etSearch.clearFocus()

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> imageView.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.round_expand_more_black_36,
                            theme
                        )
                    )
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        imageView.setImageDrawable(
                            ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.round_expand_less_black_36,
                                theme
                            )
                        )
                        etSearch.setText("")
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // binding.rlPredApps.alpha = 1 - slideOffset
                binding.rvSuggestions.alpha = 1 - slideOffset
            }
        })

        Log.v(
            "test", Utils.isBluetoothHeadsetConnected().toString() + ", " +
                    Utils.isWiredHeadsetConnected(this) +
                    ", " + Utils.getConnectionType(this) + // wrong result for Moto
                    ", " + Utils.isWifiConnected(this) + ", " + Utils.isMobileDataConnected(this) +
                    ", " + Utils.getBatteryLevel(this)
        )

        (application as SmartLauncherApplication).appListRefreshed.let { it ->
            it.observe(this, { bool ->
                if (bool) {
                    adapter?.notifyDataSetChanged()
                }
                if (it.value != bool)
                    it.value = bool
                Log.v("test-refreshObs", "${it.value}, $bool")
            })
        }

        viewModel.smartLauncherRoot?.appSuggestionsLiveData?.observe(this, {
            displayNewSuggestions2(it)
        })

        // displayNewSuggestions(viewModel.appList.take(7) as ArrayList<AppModel>)

        crashPrompt()
        if (!Utils.isMyAppLauncherDefault(this))
            askForDefaultLauncher()

        Log.v("test-oncreate", "main activity onCreate called")
        Toast.makeText(this, "Launcher onCreate called!!", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed(Runnable {
            Utils.hideKeyboard(this)
        }, 1000)

        binding.root.setOnLongClickListener {
            val popupMenu = PopupMenu(
                this,
                it,
                Gravity.NO_GRAVITY,
                R.attr.actionOverflowMenuStyle,
                0
            ) //PopupMenu(this, it)
            popupMenu.menuInflater.inflate(R.menu.activity_main_menu, popupMenu.menu)
            if (!BuildConfig.DEBUG) {
                popupMenu.menu.findItem(R.id.menu_log_files).isVisible = false
            }
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_log_files -> startLogFilesActivity()
                    R.id.menu_data_files -> startDataFilesActivity()
                }
                return@setOnMenuItemClickListener true
            }
            popupMenu.show()
            true
        }

    }

    fun askForDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            startActivityForResult(intent, 1)
        } else {
            /*val intent = Intent()
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_HOME)
            startActivity(Intent.createChooser(intent, getString(R.string.default_launcher_prompt)))*/
            // TODO show dialog prompt and then perform this action
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    fun crashPrompt() {
        val crashRestart: Boolean =
            viewModel.smartLauncherRoot?.launcherPref?.getBoolean("crash_restart", false) == true
        if (crashRestart || FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()) {
            /*val intent = Intent(this, CrashHandlerActivity::class.java)
            intent.putExtra("crash_id", mSharedPreferences.getString("crash_id", ""))
            startActivity(intent)*/
            viewModel.smartLauncherRoot?.launcherPref?.edit()?.putBoolean("crash_restart", false)
                ?.apply()
            // mSharedPreferences.edit().putString("crash_id", "").apply()
            // Crashlytics.setString("crash_id", "")
            Toast.makeText(
                this,
                "crash restart!! $crashRestart," +
                        " ${FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun searchApp(s: String) {
        viewModel.searchTextChanged(s)
        adapter?.notifyDataSetChanged()
    }

    override fun onItemClick(position: Int, appModel: AppModel, extras: Bundle?) {
        try {
            appModel.launchIntent?.let { startActivity(it) }
            viewModel.onAppClicked(appModel)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.app_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onItemLongPress(view: View, appModel: AppModel, extras: Bundle?) {
        val packageName: String = appModel.packageName
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.app_popup, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_app_options -> startAppDetailsActivity(packageName)
            }
            return@setOnMenuItemClickListener true
        }
        popupMenu.show()
    }

    override fun onStart() {
        super.onStart()
        Log.v("test-onStart", "onStart called")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_DENIED
        ) {
            val posLambda = { requestLocationPermission() }
            val negLambda = {}
            Utils.showAlertDialog(
                this, getString(R.string.need_location_permission),
                getString(R.string.location_permission_rationale),
                posLambda, negLambda
            )
        } else { // granted
            if (Utils.isLocationEnabled(this)) {
                val fusedLocationClient: FusedLocationProviderClient =
                    LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient.getCurrentLocation(
                    LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).addOnSuccessListener { location ->
                    location?.let { viewModel.updateLocationCache(it) }
                }
            } else {
                Toast.makeText(
                    this, getString(R.string.location_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.v("test-onStop", "onStop called")
    }

    fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if ((ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) ==
                                PackageManager.PERMISSION_GRANTED)
                    ) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(
                        this, getString(R.string.location_denied_consequence),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
    }

    override fun onBackPressed() {
        // disable backpress to prevent launching of system launcher TODO find better way!
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    /*override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }*/

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (sheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                val outRect = Rect()
                binding.appListSheet.getGlobalVisibleRect(outRect)
                if (!outRect.contains(
                        event.rawX.toInt(),
                        event.rawY.toInt()
                    )
                ) sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    fun displayNewSuggestions(apps: ArrayList<AppModel>) {
        // Or we could use a RecyclerView + same Adapter
        appPredViewGroup.removeAllViews()
        val width =
            Resources.getSystem().displayMetrics.widthPixels/* -
                    2 * Utils.convertDpToPixel(resources.getDimension(R.dimen.margin_default))*/
        // var appIconDim = Utils.convertDpToPixel(resources.getDimension(R.dimen.app_icon_dim))
        val appsPerRow = resources.getInteger(R.integer.app_per_row)
        // var horizontalSpace = (width - appIconDim * appsPerRow) / (appsPerRow - 1)
        //if(horizontalSpace < 20){
        val block = width / appsPerRow
        val appIconDim = (0.8 * block).toFloat()
        var horizontalSpace = block - appIconDim
        //}
        val verticalSpace =
            Utils.convertDpToPixel(resources.getDimension(R.dimen.app_vertical_space))

        Log.d(
            "test-predDraw", "width: $width, iconDim: $appIconDim, hs: $horizontalSpace, " +
                    "vs: $verticalSpace"
        )

        var prevView: View? = null
        var belowId: Int? = null
        var tableRow: TableRow? = null
        for ((idx: Int, appModel: AppModel) in apps.withIndex()) {
            val appView: View = layoutInflater.inflate(R.layout.item_app, null)
            val col = (idx % appsPerRow)
            val row = idx / appsPerRow
            if (col == 0) {
                tableRow = TableRow(this)
                val layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                tableRow.gravity = Gravity.CENTER
                tableRow.weightSum = appsPerRow.toFloat()
                tableRow?.layoutParams = layoutParams
            }
            appView.findViewById<ImageView>(R.id.iv_app).setImageDrawable(appModel.appIcon)
            appView.findViewById<TextView>(R.id.tv_app_name).visibility = View.GONE
            val trLayoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT, 1f
            )
            trLayoutParams.setMargins(
                0,
                (verticalSpace / 2).toInt(),
                0,
                (verticalSpace / 2).toInt()
            )
            appView.layoutParams = trLayoutParams
            appView.setOnClickListener {
                startActivity(appModel.launchIntent)
            }
            tableRow?.addView(appView)

            if (col == appsPerRow - 1) {
                appPredViewGroup.addView(tableRow)
                tableRow = null
            }
        }

        if (tableRow != null) {
            appPredViewGroup.addView(tableRow)
        }

        appPredViewGroup.invalidate()
    }

    fun displayNewSuggestions2(apps: ArrayList<AppModel>) {
        if (appPredAdapter == null) {
            appPredAdapter = AppsRVAdapter(apps, this, object : AppsRVAdapter.IAppsRVAdapter {
                // using this anonymous instance instead of activity impl to accommodate any future processing for clicks
                // from app suggestion screen
                override fun onItemClick(position: Int, appModel: AppModel, extras: Bundle?) {
                    this@MainActivity.onItemClick(position, appModel, extras)
                }

                override fun onItemLongPress(view: View, appModel: AppModel, extras: Bundle?) {
                    this@MainActivity.onItemLongPress(view, appModel, extras)
                }
            })
            val appsPerRow = resources.getInteger(R.integer.app_per_row)
            binding.rvSuggestions.layoutManager = GridLayoutManager(this, appsPerRow)
            binding.rvSuggestions.addItemDecoration(
                RVItemDecoration(
                    appsPerRow,
                    getResources().getDimensionPixelSize(R.dimen.margin_default), true
                )
            )
            binding.rvSuggestions.adapter = appPredAdapter
        }
        appPredAdapter?.notifyDataSetChanged()
    }

    override fun onClick(v: View?) {
        val id = v?.id
        when (id) {
            R.id.iv_right_cross -> etSearch.setText("")
        }
    }

    fun startAppDetailsActivity(packageName: String) {
        try {
            //Open the specific App Info page:
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            //e.printStackTrace();
            //Open the generic Apps page:
            val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            startActivity(intent)
        }
    }

    fun startLogFilesActivity() {
        val intent = Intent(this, LogsActivity::class.java)
        startActivity(intent)
    }

    fun startDataFilesActivity() {
        val intent = Intent(this, BackupActivity::class.java)
        startActivity(intent)
    }

    override fun refreshAppList(apps: ArrayList<AppModel>) {
        adapter?.let {
            it.appModels = apps
            it.notifyDataSetChanged()
            Log.v("test-refreshAppListAct", "called in activity")
        }
    }
}