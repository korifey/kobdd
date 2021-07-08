package org.kobdd

enum class BinopKind {
    And,
    Or,
    Xor,
    Impl
    ;

    //number of possible kinds must fit in 4 bits

}

class Opcache(val bits: Int = 20) {
    init {
        require(bits >= 0) {"`bits` must be >= 0"}
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

        // compressed = |4 bits: binop| MAX_CAPACITY_BITS: node1| MAX_CAPACITY_BITS:node2
        val compressed = node2.toLong() + (node1.toLong() shl Kobdd.MAX_CAPACITY_BITS) + (kind.ordinal shl 2 * Kobdd.MAX_CAPACITY_BITS)
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
}