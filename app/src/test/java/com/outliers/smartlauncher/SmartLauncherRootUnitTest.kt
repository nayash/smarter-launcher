/*
 *     Copyright (C) 2021.  Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.outliers.smartlauncher

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.collection.ArrayMap
import androidx.test.core.app.ApplicationProvider
import com.outliers.smartlauncher.core.SmartLauncherRoot
import com.outliers.smartlauncher.models.AppModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import kotlin.random.Random

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
    fun init() {
        slRoot = SmartLauncherRoot.getInstance(
            context,
            coroutinesTestRule.testDispatcher
        )  // coroutinesTestRule.testDispatcher
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
    fun allInstalledAppsNonEmpty() {
        println("test = allInstalledAppsNonEmpty")
        assert(slRoot!!.allInstalledApps.size > 0)
    }

    @Test
    fun appLaunchSeqLenAlways3() {
        println("test = appLaunchSeqLenAlways3")
        for (i in 0 until 5) {
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
            Thread.sleep(200)
        }
        // Thread.sleep(1000) // since nothing works for coroutine test
        println(slRoot!!.launchSequence.size)
        assert(slRoot!!.launchSequence.size == 3)
    }

    @Test
    fun appLaunchSeqContent() {  // same function doesn't work with Testdispatcher!!
        // coroutinesTestRule.testDispatcher.advanceUntilIdle()
        // coroutinesTestRule.testDispatcher.pauseDispatcher()
        // coroutinesTestRule.testDispatcher.runCurrent()
        println("test = appLaunchSeqContent")
        for (i in 0 until 5) {
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[i].packageName)
            // Thread.sleep(200)
        }
        assert(slRoot!!.launchSequence[0].endsWith("2"))
        val size = slRoot!!.launchSequence.size
        assert(slRoot!!.launchSequence[size - 1].endsWith("4"))
    }

    @Test
    fun appToIdxTest() {
        println("test = appToIdxTest")
        val appToIdxMap = slRoot!!.appToIdxMap
        println(appToIdxMap)
        assert(appToIdxMap.size == slRoot!!.allInstalledApps.size)
    }

    @Test
    fun launchHistSizeTest() {
        println("test = launchHistSize")
        for (i in 0 until 10) {
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
        }
        println(slRoot?.launchHistoryList?.map { it.key })
        assertEquals("${slRoot?.launchHistoryList}", slRoot!!.launchHistoryList.size, 10)
    }

    @Test
    fun findKNNSizeTest() {
        println("test = findKNNSize")
        for (i in 0 until 10) {
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
        }
        println("res ${slRoot?.appSuggestions?.size}")
        assert(slRoot?.appSuggestions?.size!! <= SmartLauncherRoot.APP_SUGGESTION_COUNT)
    }

    @Test
    fun launchVecAtfTest() {
        println("test = launchVecAtfTest")
        val appIdxs = arrayOf(10, 2, 25)
        slRoot
        for (i in appIdxs.indices) { // mimic launch of 3 apps with indices as above
            println("launchVecAtfTest: calling appLaunched ${appIdxs[i]}")
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[appIdxs[i]].packageName)
        }

        // mimic launch of any random app
        slRoot!!.appLaunched(slRoot!!.allInstalledApps[24].packageName)
        val launchVec = slRoot?.launchHistoryList?.last()?.value
        val atfVec = launchVec?.getSubVector(
            SmartLauncherRoot.EXPLICIT_FEATURES_COUNT,
            slRoot!!.allInstalledApps.size
        )
        val decay = SmartLauncherRoot.APP_USAGE_DECAY_RATE
        val decayVals = ArrayMap<Int, Double>()
        for ((i, idx) in appIdxs.reversed().withIndex()) {
            decayVals.put(idx, Math.pow(decay, i.toDouble()))
        }
        assertNotNull(atfVec)
        if (atfVec != null) {
            var counter = 0
            for (double in atfVec) {
                if (counter in appIdxs) {
                    assertEquals(atfVec.getEntry(counter), decayVals.get(counter))
                } else {
                    assertEquals(atfVec.getEntry(counter), 0.0, 0.0)
                }
                counter++
            }
        }
    }

    /*@Test
    fun appInstallTest() {
        */
    /**
     * Check if
     * 1. launchHistory dim increased
     * 2. new launchVec dim is correct
     * 3. 0.0 is inserted to correct idx in history vecs
     *//*

        for (i in 0 until 3) { // mimic launch of 3 random apps
            slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
        }
        val oldDim = slRoot!!.launchHistoryList.last().value.dimension
        val newApp = AppModel.getRandomApps(1)[0]
        newApp.packageName = "com.test.package44"
        newApp.appName = "AppNum44"
        slRoot!!.refreshAppList(1, newApp.packageName)
        slRoot!!.appLaunched(slRoot!!.allInstalledApps[random.nextInt(0, APP_SIZE)].packageName)
        assertEquals(oldDim + 1, slRoot!!.launchHistoryList.last().value.dimension)

        // fails because appModels in cleared and refetched internally in the "refresh" function
        // which is actually EmptyList. Try Spy of SLRoot
    }*/

    @Test
    fun appUninstallTest() {
        /**
         * Check if
         * 1. launchHistory dim reduced
         * 2. new launchVec dim is correct
         * 3. correct idx value is removed from history vecs
         */
    }

    @Test
    fun saveStateTest() {

    }

    @Test
    fun loadStateTest() {

    }

    @Test
    fun cleanUpHistoryTest() {

    }

    @After
    fun finish() {
        println("test teardown")
    }
}