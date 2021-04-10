package com.outliers.smartlauncher.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.core.MainViewModel
import com.outliers.smartlauncher.core.RVItemDecoration
import com.outliers.smartlauncher.core.SmartLauncherApplication
import com.outliers.smartlauncher.databinding.ActivityMainBinding
import com.outliers.smartlauncher.models.AppModel
import com.outliers.smartlauncher.utils.Utils


class MainActivity : AppCompatActivity(), AppsRVAdapter.IAppsRVAdapter {

    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    val viewModel by lazy { ViewModelProviders.of(this).get(MainViewModel::class.java) }
    lateinit var adapter: AppsRVAdapter
    val sheetBehavior by lazy { BottomSheetBehavior.from(binding.appListSheet) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val etSearch: EditText = binding.appListSheet.findViewById(R.id.et_search)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                searchApp(s.toString())
            }
        })

        val imageView = binding.appListSheet.findViewById<ImageView>(R.id.iv_expand)
        val rvApps = binding.appListSheet.findViewById<RecyclerView>(R.id.rv_apps)
        adapter = AppsRVAdapter(viewModel.appList, this, this)
        rvApps.layoutManager = GridLayoutManager(this, 4)
        rvApps.addItemDecoration(
            RVItemDecoration(
                4,
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
                    BottomSheetBehavior.STATE_COLLAPSED -> imageView.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.round_expand_less_black_36,
                            theme
                        )
                    )
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })
        Utils.hideKeyboard(this)
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
                    adapter.notifyDataSetChanged()
                }
                if (it.value != bool)
                    it.value = bool
                Log.v("test-refreshObs", "${it.value}, $bool")
            }) }

        Log.v("test-onCreate", "onCreate called")
    }

    fun searchApp(s: String) {
        viewModel.searchTextChanged(s)
        adapter.notifyDataSetChanged()
    }

    override fun onItemClick(position: Int, appModel: AppModel, extras: Bundle?) {
        try {
            appModel.launchIntent?.let { startActivity(it) }
            viewModel.onAppClicked(appModel)
        }catch (ex: ActivityNotFoundException){
            Toast.makeText(this, getString(R.string.app_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        Log.v("test-onStart", "onStart called")

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_DENIED){
            val posLambda = {requestLocationPermission()}
            val negLambda = {}
            Utils.showAlertDialog(
                this, getString(R.string.need_location_permission),
                getString(R.string.location_permission_rationale),
                posLambda, negLambda
            )
        }
    }

    override fun onStop() {
        super.onStop()
        Log.v("test-onStop", "onStop called")
    }

    fun requestLocationPermission(){
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
            if (sheetBehavior.getState() === BottomSheetBehavior.STATE_EXPANDED) {
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
}