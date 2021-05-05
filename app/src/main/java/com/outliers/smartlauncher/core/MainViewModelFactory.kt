package com.outliers.smartlauncher.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders

class MainViewModelFactory(
    val application: SmartLauncherApplication,
    val parentView: MainViewModel.MainVMParent
) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return when(modelClass){
            MainViewModel::class.java -> MainViewModel(application, parentView)
            else -> throw IllegalArgumentException("view class not supported")
        } as T
        /*return modelClass.getConstructor(
            SmartLauncherApplication::class.java,
            MainViewModel.MainVMParent::class.java
        ).newInstance(application, parentView)*/
    }

}