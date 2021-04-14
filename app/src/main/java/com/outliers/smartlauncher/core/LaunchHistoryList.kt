package com.outliers.smartlauncher.core

class LaunchHistoryList<K, V>(size: Int = 100): Collection<Tuple<K, V>>{
    private val historyList: ArrayList<Tuple<K, V>> = ArrayList()

    operator fun get(index: Int): Tuple<K, V>{
        return historyList[index]
    }

    fun add(key: K, value: V){
        if(key == null || value == null)
            throw NullPointerException("key or value is null. Values passed are: $key, $value")
        historyList.add(Tuple(key, value))
    }

    fun removeAt(index: Int){
        if(index < 0 || index >= size)
            throw IndexOutOfBoundsException("Size is $size but index passed is $index")
        historyList.removeAt(index)
    }

    private fun getIndexOfKey(key: K): Int{
        for((i, tuple) in historyList.withIndex()) {
            if (tuple.key?.equals(key) == true) {
                return i
            }
        }
        return -1
    }

    fun removeEntriesWithKey(key: K){
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

    fun getValueAt(index: Int): V?{
        return historyList[index].value
    }

    fun updateValueAt(index: Int, newValue: V){
        if(index < 0 || index >= size)
            throw IndexOutOfBoundsException("Size is $size but index passed is $index")

        historyList[index].value = newValue
    }

    override val size: Int
    get() = historyList.size

    override fun iterator(): Iterator<Tuple<K, V>> {
        return object : MutableIterator<Tuple<K, V>> {
            private var currentIndex = 0
            override fun hasNext(): Boolean {
                return currentIndex < size && historyList.get(currentIndex) != null
            }

            override fun next(): Tuple<K, V> {
                return historyList[currentIndex++]
            }

            override fun remove() {
                removeAt(currentIndex)  // TODO test before using this
            }
        }
    }

    override fun contains(element: Tuple<K, V>): Boolean {
        for(tuple in historyList){
            if(element.equals(tuple))
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

    fun clear(){
        historyList.clear()
    }

    override fun toString(): String {
        val sb: StringBuilder = StringBuilder()
        for(tuple in historyList){
            sb.append(tuple.toString())
        }
        return sb.toString()
    }
}