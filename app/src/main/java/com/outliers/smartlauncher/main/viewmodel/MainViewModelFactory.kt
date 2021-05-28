/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.outliers.smartlauncher.core.SmartLauncherApplication

class MainViewModelFactory(
    val application: SmartLauncherApplication,
    val parentView: MainViewModel.MainVMParent
) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return when (modelClass) {
            MainViewModel::class.java -> MainViewModel(application, parentView)
            else -> throw IllegalArgumentException("view class not supported")
        } as T
        /*return modelClass.getConstructor(
            SmartLauncherApplication::class.java,
            MainViewModel.MainVMParent::class.java
        ).newInstance(application, parentView)*/
    }

}