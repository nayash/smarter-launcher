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

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.outliers.smartlauncher.core.SmartLauncherRoot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.collections4.map.LinkedMap
import org.apache.commons.math3.linear.ArrayRealVector
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class SmartLauncherRootInstTest {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    var slRoot: SmartLauncherRoot? = null

    @Before
    fun init() {
        slRoot = SmartLauncherRoot.getInstance(appContext)
    }

    @Test
    fun singletonTest() {
        val slRoot2 = SmartLauncherRoot.getInstance(appContext)
        assert(slRoot2 == slRoot)
    }

    @Test
    fun installedPackagesNonEmpty() {
        assert(slRoot?.allInstalledApps!!.size > 0)
    }

    @Test
    fun packageHashCodeSameOnRerun() {
        /*val map1 = slRoot?.appToIdMap
        slRoot?.let { it.initPackageToIdMap(it.allInstalledApps, it.appToIdMap) }
        val map2 = slRoot?.appToIdMap
        val res: Boolean? = map1?.equals(map2)
        res?.let {
            if(!it)
                throw Exception("maps not identical")
        }*/
    }

    @Test
    fun miscTest() {
        val size = 20
        var realVec = ArrayRealVector(size)
        realVec = realVec.append(10.9) as ArrayRealVector
        assert(realVec.dimension == size + 1)
        assert(realVec.getEntry(size) == 10.9)
        Log.v("test", realVec.toString())

        var vec2 = ArrayRealVector(size)
        for (i in 0 until size)
            vec2.setEntry(i, i.toDouble())
        val idxToRemove = 10
        val newVec = vec2.getSubVector(0, idxToRemove).append(
            vec2.getSubVector(idxToRemove + 1, vec2.dimension - (idxToRemove + 1))
        )
        Log.v("test2", newVec.toString())

        val map = LinkedMap<String, ArrayRealVector>()
        map.put("a", realVec)
        Log.v("testMap1", "$map")
        map.put("a", vec2)
        Log.v("testMap2", "$map")
    }

    @Test
    fun cleanUpHistoryTest() {
        val appSize = slRoot?.allInstalledApps?.size ?: 100
        val historyList = slRoot?.launchHistoryList
        for (i in 0 until 10) {
            historyList?.add(slRoot!!.allInstalledApps[0].packageName, ArrayRealVector(appSize))
        }
        historyList?.add(slRoot!!.allInstalledApps[1].packageName, ArrayRealVector(appSize))
        println("test1 $historyList")
        assert(historyList?.size == 11)
        GlobalScope.launch { slRoot?.cleanUpHistory() }
        println("test2 $historyList")
        assert(historyList?.size == 10)
    }
}