package com.outliers.smartlauncher

import android.content.Context
import com.outliers.smartlauncher.core.SmartLauncherRoot
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class SmartLauncherRootUnitTest {

    var slRoot: SmartLauncherRoot? = null

    @Before
    fun init(){
        val mockContext = Mockito.mock(Context::class.java)
        slRoot = SmartLauncherRoot.getInstance(mockContext)
    }

    @Test
    fun allInstalledAppsNonEmpty(){
        // test in the instrumented test
    }

    @Test
    fun appToIdMapNonEmpty(){

    }

    @After
    fun finish(){

    }
}