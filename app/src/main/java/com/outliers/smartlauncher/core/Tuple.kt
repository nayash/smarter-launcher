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

class Tuple<K, V>(key_: K, value_: V) {
    // TODO use Kotlin Pair instead
    val key: K = key_

    var value: V = value_

    override fun equals(other: Any?): Boolean {
        return (other is Tuple<*, *>)
                && key == other.key
                && value == other.value
    }

    override fun toString(): String {
        return "{$key: $value}"
    }
}