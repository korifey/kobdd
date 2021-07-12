package org.kobdd

import org.kobdd.Kobdd.Companion.MAX_CAPACITY_BITS

enum class BinopKind {
    And,
    Or,
    Xor,
    Impl
    ;

    //number of possible kinds must fit in 4 bits

}

class Opcache(val bits: Int = 20) {
    private val bitsForHeader = 32 - 2 * MAX_CAPACITY_BITS
    private val maskForUnaryOp: Int
    init {
        require(bits >= 0) {"`bits` must be >= 0"}
        require(BinopKind.values().size < (1 shl bitsForHeader) - 1) { "Not enough bits to represent header" }

        maskForUnaryOp = (1 shl bitsForHeader) - 1
    }




    private val size = 1 shl (bits-1)
    val mask = (size - 1).toLong()

    val storage = LongArray(size)
    val result = IntArray(size)

    //TODO not sure whether it's a good mixer: translator from [compressed] -> [index]
    inline fun index(compressed: Long) : Int {
        var index = 0
        var rem = compressed
        while (rem > 0) {
            index = index xor (rem and mask).toInt()
            rem = rem ushr bits
        }
        return index
    }

    inline fun binop(kind: BinopKind, node1: Int, node2: Int, produce: () -> Int) : Int {
        assert(node1 >= 0 && node1 < Kobdd.MAX_CAPACITY)
        assert(node2 >= 0 && node2 < Kobdd.MAX_CAPACITY)

        return raw(kind.ordinal, node1, node2, produce)
    }

    inline fun raw(header: Int, high: Int, low: Int, produce: () -> Int) : Int {
        assert(high >= 0 && high < Kobdd.MAX_CAPACITY)
        assert(low >= 0 && low < Kobdd.MAX_CAPACITY)

        // compressed = |4 bits: binop| MAX_CAPACITY_BITS: node1| MAX_CAPACITY_BITS:node2
        val compressed = low.toLong() + (high.toLong() shl MAX_CAPACITY_BITS) + (header shl 2 * MAX_CAPACITY_BITS)
        assert(compressed > 0) {"Can't distinguish from 0 placeholder in storage array"}

        val index = index(compressed)

        /* Direct mapped cache */
        if (storage[index] != compressed) {
            result[index] = produce()
            //order is critical - if produce() throws exception we mustn't modify storage[index]
            storage[index] = compressed

        }
        return result[index]
    }




    inner class UnaryOpCache(val id: Int) {
        val header = (1 shl bitsForHeader) - 1

        inline fun getOrPut(node: Int, produce: () -> Int) : Int {
            assert(node >= 0 && node < Kobdd.MAX_CAPACITY)

            return raw(header, id, node, produce)
        }
    }

    private var nextIdForUnaryOp = 0
    fun createCacheForUnaryOp() : UnaryOpCache {
        val id = nextIdForUnaryOp
        require(id < Kobdd.MAX_CAPACITY) {"Too many caches created, please invoke clear() manually"}
        nextIdForUnaryOp++
        return UnaryOpCache(id)
    }

    fun clear() {
        nextIdForUnaryOp = 0
        storage.fill(0)
    }
}