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

package com.outliers.smartlauncher.core

import java.util.*

class LaunchHistoryList<K, V>(size: Int = 100) : Collection<Tuple<K, V>> {
    private val historyList: MutableList<Tuple<K, V>> =
        Collections.synchronizedList(mutableListOf<Tuple<K, V>>())

    operator fun get(index: Int): Tuple<K, V> {
        return historyList[index]
    }

    fun add(key: K, value: V) {
        if (key == null || value == null)
            throw NullPointerException("key or value is null. Values passed are: $key, $value")
        historyList.add(Tuple(key, value))
    }

    fun removeAt(index: Int) {
        if (index < 0 || index >= size)
            throw IndexOutOfBoundsException("Size is $size but index passed is $index")
        historyList.removeAt(index)
    }

    private fun getFirstIndexOfKey(key: K): Int {
        for ((i, tuple) in historyList.withIndex()) {
            if (tuple.key?.equals(key) == true) {
                return i
            }
        }
        return -1
    }

    fun removeEntriesWithKey(key: K) {
        val it: MutableIterator<Tuple<K, V>> = historyList.iterator()
        while (it.hasNext()) {
            if (it.next().key?.equals(key) == true) {
                it.remove()
            }
        }
    }

    /*fun getValue(key: K): V?{
        val idx = getIndexOfKey(key)
        if(idx > -1) {
            historyList[idx].value
        }
        return null
    }*/

    fun getValueAt(index: Int): V? {
        if (index >= size)
            return null
        return historyList[index].value
    }

    fun updateValueAt(index: Int, newValue: V) {
        if (index < 0 || index >= size)
            throw IndexOutOfBoundsException("Size is $size but index passed is $index")

        historyList[index].value = newValue
    }

    override val size: Int
        get() = historyList.size

    override fun iterator(): MutableIterator<Tuple<K, V>> {
        return historyList.listIterator()
    }

    override fun contains(element: Tuple<K, V>): Boolean {
        for (tuple in historyList) {
            if (element.equals(tuple))
                return true
        }
        return false
    }

    fun containsKey(elementKey: K): Boolean {
        for (tuple in historyList) {
            if (tuple.key?.equals(elementKey) == true)
                return true
        }
        return false
    }

    override fun containsAll(elements: Collection<Tuple<K, V>>): Boolean {
        throw UnsupportedOperationException("this operation is not implemented yet")
    }

    override fun isEmpty(): Boolean {
        return size == 0
    }

    fun clear() {
        historyList.clear()
    }

    override fun toString(): String {
        val sb: StringBuilder = StringBuilder()
        for (tuple in historyList) {
            sb.append(tuple.toString())
        }
        return sb.toString()
    }
}