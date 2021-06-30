package org.korifey

import java.lang.ref.SoftReference
import java.util.*

class KBdd private constructor(nodeIndex: Int) {

    companion object {
        const val tableSize = (1 shl 24) * 2
        var nodesStorage = LongArray(tableSize)
        var nodesCount = 0

        internal val refs = ArrayList<SoftReference<KBdd>>()

        internal val trueNodeIndex = 1
        internal val falseNodeIndex = 0

        internal fun node(i: Int) = Node(nodesStorage[i])
    }

    init {
        refs.add(SoftReference(this))
        //todo clear sometimes
    }

    // variable (16 bit) | trueBranch (24 bit) | falseBranch(24 bit)
    //
    internal inline class Node(val encoded: Long) {
        val variable : Int get() = (encoded ushr 48).toInt()
        val trueBranch: Int get() = ((encoded ushr 24) and (0xFFFFFFFFL)).toInt()
        val falseBranch: Int get() = (encoded and 0xFFFFFFFFL).toInt()

        val isFalse : Boolean get() = TODO()
        val isTrue : Boolean get() = TODO()
    }
}

private fun mkNode(variable: Int, onTrue: Int, onFalse: Int) : Int {
    if (onTrue == onFalse) return onTrue
    return TODO()
}

fun mkVar(x: Int) : KBdd =TODO()
