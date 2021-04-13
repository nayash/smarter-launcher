package com.outliers.smartlauncher

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.outliers.smartlauncher.core.SmartLauncherRoot
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.RealVector
import org.junit.Before
import org.junit.Test

class SmartLauncherRootInstTest {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    var slRoot: SmartLauncherRoot? = null

    @Before
    fun init(){
        slRoot = SmartLauncherRoot.getInstance(appContext)
    }

    @Test
    fun installedPackagesNonEmpty(){
        assert(slRoot?.allInstalledApps!!.size > 0)
    }

    @Test
    fun mapLenSameAsApps(){
        assert(slRoot?.allInstalledApps?.size == slRoot?.appToIdMap?.size)
    }

    @Test
    fun packageHashCodeSameOnRerun(){
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
    fun miscTest(){
        val size = 20
        var realVec = ArrayRealVector(size)
        realVec = realVec.append(10.9) as ArrayRealVector
        assert(realVec.dimension == size+1)
        assert(realVec.getEntry(size) == 10.9)
        Log.v("test", realVec.toString())

        var vec2 = ArrayRealVector(size)
        for(i in 0 until size)
            vec2.setEntry(i, i.toDouble())
        val idxToRemove = 10
        val newVec = vec2.getSubVector(0, idxToRemove).append(
            vec2.getSubVector(idxToRemove+1, vec2.dimension-(idxToRemove+1)))
        Log.v("test2", newVec.toString())
    }
}