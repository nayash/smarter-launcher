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

import com.outliers.smartlauncher.utils.Utils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.Math.abs

@RunWith(JUnit4::class)
class UtilsTest {

    @Before
    fun init() {}

    @Test
    fun minMaxScaleTest_std() {
        var min = -90.0; var max = 90.0; var newMin = 0.0; var newMax = 1.0
        var x = arrayOf(-90.0, 0.0, 90.0, 45.0)
        var expectedRes = arrayOf(0.0, 0.5, 1.0, 0.75)
        for ((num, exRes) in x.zip(expectedRes)) {
            var res = Utils.minMaxScale(num, min, max, newMin, newMax)
            println("$num, $exRes, $res")
            assert(res == exRes)
        }
    }

    @Test
    fun minMaxScaleTest_nonstd() {
        var min = -90.0; var max = 90.0; var newMin = -5.0; var newMax = 1.0
        var x = arrayOf(-90.0, 0.0, 90.0, 45.0, -0.35)
        var expectedRes = arrayOf(-5.0, -2.0, 1.0, -0.5, -2.0117)
        for ((num, exRes) in x.zip(expectedRes)) {
            var res = Utils.minMaxScale(num, min, max, newMin, newMax)
            println("$num, $exRes, $exRes")
            assert(approxEquals(exRes, res, 1e-4))
        }
    }

    fun approxEquals(value: Double, other: Double, epsilon: Double): Boolean {
        return kotlin.math.abs(value - other) < epsilon
    }

    @After
    fun finish() {}
}