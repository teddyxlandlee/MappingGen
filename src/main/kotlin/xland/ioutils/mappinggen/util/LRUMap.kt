package xland.ioutils.mappinggen.util

open class LRUMap<K, V>(
    private val maxCapacity: Int = 2 shl 18,
    initialCapacity : Int = 16) : LinkedHashMap<K, V>(initialCapacity, 0.75f, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxCapacity
    }
}