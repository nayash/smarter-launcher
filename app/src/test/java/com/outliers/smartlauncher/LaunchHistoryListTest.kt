package com.outliers.smartlauncher

import android.util.Log
import com.google.gson.Gson
import com.outliers.smartlauncher.core.LaunchHistoryList
import com.outliers.smartlauncher.core.Tuple
import org.apache.commons.math3.linear.ArrayRealVector
import org.junit.Before
import org.junit.Test
import java.lang.IndexOutOfBoundsException

class LaunchHistoryListTest {

    val launchHistoryList: LaunchHistoryList<String, ArrayRealVector> = LaunchHistoryList()
    val size = 10
    val vecSize = 20

    @Before
    fun init(){
        for(i in 0 until size){
            launchHistoryList.add(i.toString(), ArrayRealVector(vecSize))
        }
    }

    @Test
    fun dataCheck(){
        assert(launchHistoryList.size == size)
        launchHistoryList.getValueAt(0)?.setEntry(2, 2.0)
        assert(launchHistoryList.getValueAt(0)?.getEntry(2) == 2.0)
        assert(launchHistoryList[3].key == "3")
    }

    @Test
    fun removeAtTest(){
        launchHistoryList.removeAt(4)
        assert(launchHistoryList.size == size-1)
        assert(launchHistoryList[4].key == "5")
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun removeAtIOBTest(){
        launchHistoryList.removeAt(size+1)
    }

    @Test
    fun removeEntriesWithKeyTest(){
        val testList = LaunchHistoryList<String, ArrayRealVector>()
        val keys = arrayListOf("a", "b", "c", "a", "b", "d", "e")
        for(key in keys){
            testList.add(key, ArrayRealVector(10))
        }

        val keyToDelete = "a"
        testList.removeEntriesWithKey(keyToDelete)
        for(tuple in testList){
            assert(tuple.key != keyToDelete)
        }
    }

    @Test
    fun formatTest(){
        val tuple = Tuple("a", ArrayRealVector(5))
        val tempList = LaunchHistoryList<String, ArrayRealVector>()
        tempList.add(tuple.key, tuple.value)
        val jsonStr = Gson().toJson(tempList).replace(" ", "")
        println(jsonStr)
        assert(jsonStr == "[{\"key\":\"a\",\"value\":{\"data\":[0.0,0.0,0.0,0.0,0.0]}}]")
    }
}