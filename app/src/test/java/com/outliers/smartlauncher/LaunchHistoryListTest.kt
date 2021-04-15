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
    lateinit var launchHistoryList2: LaunchHistoryList<String, ArrayRealVector>
    val size = 10
    val vecSize = 20

    @Before
    fun init(){
        for(i in 0 until size){
            launchHistoryList.add(i.toString(), ArrayRealVector(vecSize))
        }

        launchHistoryList2 = LaunchHistoryList<String, ArrayRealVector>()
        for(i in 0 until 10){
            val vec = ArrayRealVector(5)
            for(j in 0 until vec.dimension){
                vec.setEntry(j, (i+j).toDouble())
            }
            launchHistoryList2.add(i.toString(), vec)
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
        assert(testList.size == 5)
    }

    @Test
    fun getValueAt(){
        val testList = launchHistoryList2

        assert(testList.getValueAt(1) == ArrayRealVector(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)))
        assert(testList.getValueAt(3) == ArrayRealVector(doubleArrayOf(3.0, 4.0, 5.0, 6.0, 7.0)))
    }

    @Test
    fun updateValueAtTest(){
        val old = launchHistoryList[3].value
        val new = ArrayRealVector(doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0))
        launchHistoryList[3].value = new
        assert(old != launchHistoryList[3].value)
        assert(launchHistoryList[3].value.getEntry(4) == 5.0)
    }

    @Test
    fun clearTest(){
        // include isEmpty
        val testList = launchHistoryList2

        assert(testList.size == 10)
        testList.clear()
        assert(testList.size == 0)
        assert(testList.isEmpty())
    }

    @Test
    fun containsTest(){
        assert(launchHistoryList.contains(Tuple("1", ArrayRealVector(20))))
        assert(!launchHistoryList.contains(Tuple("11", ArrayRealVector(20))))
    }

    @Test
    fun containsKeyTest(){
        assert(launchHistoryList.containsKey("1"))
        assert(!launchHistoryList.containsKey("11"))
    }

    @Test
    fun iteratorBasicTest(){
        // include remove while iterating
        for((i, tuple) in launchHistoryList2.withIndex()){
            assert(tuple.key.equals(i.toString()))
            assert(tuple.value.getEntry(0) == i.toDouble())
            assert(tuple.value.getEntry(4) == i.toDouble()+4)
        }

        val iterator = launchHistoryList2.iterator()
        var i = 0
        while(iterator.hasNext()){
            val tuple = iterator.next()
            assert(tuple.key.equals(i.toString()))
            assert(tuple.value.getEntry(0) == i.toDouble())
            assert(tuple.value.getEntry(4) == i.toDouble()+4)
            i++
        }
    }

    @Test
    fun iteratorRemoveTest(){
        val oldSize = launchHistoryList2.size
        val iterator = launchHistoryList2.iterator()
        var i = 0
        while(iterator.hasNext()){
            val tuple = iterator.next()
            if(tuple.key == "4"){
                iterator.remove()
            }
            i++
        }
        assert(launchHistoryList2.size == oldSize-1)
        assert(!launchHistoryList2.containsKey("4"))
        assert(launchHistoryList2[4].key == "5")
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