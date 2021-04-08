package com.outliers.smartlauncher

import androidx.test.platform.app.InstrumentationRegistry
import com.outliers.smartlauncher.core.SmartLauncherRoot
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
        val map1 = slRoot?.appToIdMap
        slRoot?.let { it.initPackageToIdMap(it.appModels, it.appToIdMap) }
        val map2 = slRoot?.appToIdMap
        val res: Boolean? = map1?.equals(map2)
        res?.let {
            if(!it)
                throw Exception("maps not identical")
        }
    }
}