package com.outliers.smartlauncher

import android.content.Context
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.outliers.smartlauncher.core.SmartLauncherRoot
import com.outliers.smartlauncher.models.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
//@RunWith(MockitoJUnitRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@ExperimentalCoroutinesApi
class SmartLauncherRootUnitTest {

    @get:Rule
    val instantTestExecutorRule = InstantTaskExecutorRule()
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()
    var slRoot: SmartLauncherRoot? = null
    val context = ApplicationProvider.getApplicationContext<Context>()
    lateinit var slRootSpy: SmartLauncherRoot

    @Before
    fun init(){
        slRoot = SmartLauncherRoot.getInstance(context, coroutinesTestRule.testDispatcher)  // coroutinesTestRule.testDispatcher
        slRoot?.appModels?.addAll(AppModel.getRandomApps(5))
        slRoot?.initPackageToIdMap()
    }

    @Test
    fun allInstalledAppsNonEmpty(){
        assert(slRoot!!.allInstalledApps.size > 0)
    }

    @Test
    fun appLaunchSeqLenAlways3() = coroutinesTestRule.testDispatcher.runBlockingTest{
        for(i in 0 until 5){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[i].packageName)
        }
        Thread.sleep(1000) // since nothing works for coroutine test
        println(slRoot!!.launchSequence.size)
        assert(slRoot!!.launchSequence.size == 3)
    }

    @Test
    fun appLaunchSeqContent(){  // same function doesn't work with Testdispatcher!!
        for(i in 0 until 5){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[i].packageName)
            Thread.sleep(500)
        }
        // coroutinesTestRule.testDispatcher.advanceUntilIdle()
        //coroutinesTestRule.testDispatcher.pauseDispatcher()
        //coroutinesTestRule.testDispatcher.runCurrent()
        assert(slRoot!!.launchSequence[0].endsWith("2"))
        val size = slRoot!!.launchSequence.size
        assert(slRoot!!.launchSequence[size-1].endsWith("4"))
    }

    @Test
    fun appToIdxTest(){
        val appToIdxMap = slRoot!!.appToIdxMap
        println(appToIdxMap)
        assert(appToIdxMap.size == slRoot!!.allInstalledApps.size)
    }

    @Test
    fun launchHistSize(){
        for(i in 0 until 10){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[0].packageName)
            Thread.sleep(100)
        }
        assert(slRoot!!.launchHistoryList.size == 10)
    }

    @After
    fun finish(){

    }
}