/*
 *  Copyright (c) 2021. Asutosh Nayak (nayak.asutosh@ymail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.outliers.smartlauncher.core

class Tuple<K, V>(key_: K, value_: V) {

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