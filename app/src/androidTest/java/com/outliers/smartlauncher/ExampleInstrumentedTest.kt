package com.outliers.smartlauncher

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.outliers.smartlauncher.core.SmartLauncherRoot

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    lateinit var slRoot: SmartLauncherRoot

    @Before
    fun init(){

    }

    @Test
    fun useAppContext() {
        // Context of the app under test.

        assertEquals("com.outliers.smartlauncher", appContext.packageName)
    }
}