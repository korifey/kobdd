package org.kobdd.util

import kotlin.math.absoluteValue
import kotlin.math.max

class PersistentBoolset(capacity: Int = 0) {
    private val array = BooleanArray(capacity)

    constructor(boolset: PersistentBoolset)  :this (boolset.array.size) {
        boolset.array.copyInto(array)
    }

    constructor(clause: List<Int>) : this( (clause.maxOfOrNull { it.absoluteValue } ?: - 1) + 1) {
        clause.forEach { array[it.absoluteValue] = true }
    }

    fun contains(v: Int) = v < array.size && array[v]

    operator fun plus(other: PersistentBoolset) : PersistentBoolset {
        val res = PersistentBoolset(max(array.size, other.array.size))
        for (i in res.array.indices) {
            if (i < array.size && array[i] || i < other.array.size && other.array[i])
                res.array[i] = true
        }
        return res
    }

    operator fun minus(v: Int) : PersistentBoolset {
        val res = PersistentBoolset(this)
        if (v < array.size) res.array[v] = false
        return res
    }

    fun toList() : List<Int> {
        val res = mutableListOf<Int>()
        for (i in array.indices) if (array[i]) res.add(i)
        return res
    }
}