package com.outliers.smartlauncher

import android.content.Context
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.outliers.smartlauncher.core.SmartLauncherRoot
import com.outliers.smartlauncher.models.AppModel
import kotlinx.coroutines.*
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
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import org.junit.Assert

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
    val APP_SIZE = 40
    lateinit var random: Random

    @Before
    fun init(){
        slRoot = SmartLauncherRoot.getInstance(context, coroutinesTestRule.testDispatcher)  // coroutinesTestRule.testDispatcher
        slRoot?.let {
            it.appModels.clear()
            it.launchHistoryList.clear()
            it.appSuggestions.clear()
            it.launchSequence.clear()
            it.appModels.addAll(AppModel.getRandomApps(APP_SIZE))
            it.initPackageToIdMap()
        }
        random = Random(999)
        println("test setup done")
    }

    @Test
    fun allInstalledAppsNonEmpty(){
        println("test = allInstalledAppsNonEmpty")
        assert(slRoot!!.allInstalledApps.size > 0)
    }

    @Test
    fun appLaunchSeqLenAlways3(){
        println("test = appLaunchSeqLenAlways3")
        for(i in 0 until 5){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
            Thread.sleep(200)
        }
        // Thread.sleep(1000) // since nothing works for coroutine test
        println(slRoot!!.launchSequence.size)
        assert(slRoot!!.launchSequence.size == 3)
    }

    @Test
    fun appLaunchSeqContent(){  // same function doesn't work with Testdispatcher!!
        println("test = appLaunchSeqContent")
        for(i in 0 until 5){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[i].packageName)
            Thread.sleep(200)
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
        println("test = appToIdxTest")
        val appToIdxMap = slRoot!!.appToIdxMap
        println(appToIdxMap)
        assert(appToIdxMap.size == slRoot!!.allInstalledApps.size)
    }

    @Test
    fun launchHistSizeTest(){
        println("test = launchHistSize")
        for(i in 0 until 10){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
            Thread.sleep(100)
        }
        println(slRoot?.launchHistoryList?.map { it.key })
        Assert.assertEquals("${slRoot?.launchHistoryList}", slRoot!!.launchHistoryList.size, 10)
    }

    @Test
    fun findKNNSizeTest(){
        println("test = findKNNSize")
        for(i in 0 until 10){
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
            Thread.sleep(100)
        }
        println("res ${slRoot?.appSuggestions?.size}")
        assert(slRoot?.appSuggestions?.size == SmartLauncherRoot.APP_SUGGESTION_COUNT)
    }

    @After
    fun finish(){
        println("test teardown")
    }
}