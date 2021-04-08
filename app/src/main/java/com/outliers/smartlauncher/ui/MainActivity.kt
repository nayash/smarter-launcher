package com.outliers.smartlauncher.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.outliers.smartlauncher.R
import com.outliers.smartlauncher.core.MainViewModel
import com.outliers.smartlauncher.core.RVItemDecoration
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
                        resources.getDrawable(
                            R.drawable.round_expand_more_black_36,
                            theme
                        )
                    )
                    BottomSheetBehavior.STATE_COLLAPSED -> imageView.setImageDrawable(
                        resources.getDrawable(
                            R.drawable.round_expand_less_black_36,
                            theme
                        )
                    )
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }

        })
    }

    fun searchApp(s: String) {
        viewModel.searchTextChanged(s)
        adapter.notifyDataSetChanged()
    }

    override fun onItemClick(position: Int, appModel: AppModel, extras: Bundle?) {
        appModel.launchIntent?.let{startActivity(it)}
    }
}