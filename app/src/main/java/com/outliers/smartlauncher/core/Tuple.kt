package com.outliers.smartlauncher.core

class Tuple<K, V>(key_: K, value_: V) {

    val key: K = key_
    get() = field

    var value: V = value_
    get() = field
    set(value) {field = value}

    override fun equals(other: Any?): Boolean {
        return (other is Tuple<*, *>)
                && key == other.key
                && value == other.value
    }

    override fun toString(): String {
        return "{$key: $value}"
    }
}