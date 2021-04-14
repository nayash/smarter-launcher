package com.outliers.smartlauncher

import android.util.Log
import com.google.gson.Gson
import com.outliers.smartlauncher.core.LaunchHistoryList
import com.outliers.smartlauncher.core.Tuple
import org.apache.commons.math3.linear.ArrayRealVector
import org.junit.Before
import org.junit.Test

class LaunchHistoryListTest {

    val launchHistoryList: LaunchHistoryList<String, ArrayRealVector> = LaunchHistoryList()

    @Before
    fun init(){
        val size = 10
        val vecSize = 20
        for(i in 0 until size){
            launchHistoryList.add(i.toString(), ArrayRealVector(vecSize))
        }
    }

    @Test
    fun formatTest(){
        println("test-format ${Gson().toJson(launchHistoryList)}")
        val tuple = Tuple("a", ArrayRealVector(20))
        println(Gson().toJson(launchHistoryList[0]))
        println("${Gson().toJson(tuple)}, ${tuple.key}, ${tuple.value}")
    }
}