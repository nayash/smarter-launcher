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

import com.outliers.smartlauncher.core.Tuple
import org.apache.commons.math3.linear.ArrayRealVector
import org.junit.Before
import org.junit.Test

class TupleTest {

    lateinit var tuple: Tuple<String, ArrayRealVector>

    @Before
    fun before() {
        val arrayRealVector = ArrayRealVector(20)
        tuple = Tuple("com.test.package", arrayRealVector)
    }

    @Test
    fun getKeyTest() {
        assert(tuple.key == "com.test.package")
    }

    @Test
    fun getValueTest() {
        assert(tuple.value == ArrayRealVector(20))
        tuple.value.setEntry(17, 1.2)
        assert(tuple.value.getEntry(17) == 1.2)
    }

    @Test
    fun getValueSubArrayTest() {
        tuple.value.setSubVector(5, doubleArrayOf(1.2, 0.0, 1.3, 2.5, 1.7))
        assert(
            tuple.value.getSubVector(5, 5) == ArrayRealVector(
                doubleArrayOf(
                    1.2,
                    0.0,
                    1.3,
                    2.5,
                    1.7
                )
            )
        )
        assert(tuple.value.getEntry(9) == 1.7)
    }

    @Test
    fun equalsNegTest() {
        var other = Tuple("com.test.package1", ArrayRealVector(20))
        other.value.setEntry(10, 1.5)
        assert(!tuple.equals(other))

        other = Tuple("com.test.package", ArrayRealVector(20))
        assert(tuple.equals(other))

        val other1 = Tuple("a", 1.2)
        assert(!other1.equals(tuple))
    }

    @Test
    fun equalsPosTest() {
        var other = Tuple("com.test.package", ArrayRealVector(20))
        assert(tuple.equals(other))
        assert(tuple == other)

        val vec = ArrayRealVector(20)
        vec.setEntry(17, 1.2)
        other = Tuple("com.test.package", vec)
        tuple.value.setEntry(17, 1.2)
        assert(tuple == other)
    }

    @Test
    fun equalsDiffKeyTest() {
        val vec = ArrayRealVector(20)
        vec.setEntry(17, 1.2)
        val other = Tuple("com.test.package1", vec)
        assert(!tuple.equals(other))
    }

    @Test
    fun equalsDiffValueTest() {
        val vec = ArrayRealVector(20)
        val other = Tuple("com.test.package1", vec)
        assert(!tuple.equals(other))
    }
}