package com.outliers.smartlauncher.ui

import android.content.ActivityNotFoundException
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
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

        val bottomSheet: View = binding.appListSheet
        val sheetBehaviour = BottomSheetBehavior.from(bottomSheet)
        sheetBehaviour.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> imageView.setImageDrawable(
                        ResourcesCompat.getDrawable(resources,
                            R.drawable.round_expand_more_black_36,
                            theme
                        )
                    )
                    BottomSheetBehavior.STATE_COLLAPSED -> imageView.setImageDrawable(
                        ResourcesCompat.getDrawable(resources,
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
        Log.v("test", Utils.isHeadsetConnected(this).toString()+
                ", "+Utils.getConnectionType(this)+
        ", "+ Utils.isWifiConnected(this)+ ", "+ Utils.isMobileDataConnected(this)+
        ", "+ Utils.getBatteryLevel(this))

        (application as SmartLauncherApplication).appListRefreshed.let { it ->
            it.observe(this, { bool ->
                if(bool){
                    adapter.notifyDataSetChanged()
                }
                if(it.value != bool)
                    it.value = bool
                Log.v("test-refreshObs", "${it.value}, $bool")
        }) }
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
}