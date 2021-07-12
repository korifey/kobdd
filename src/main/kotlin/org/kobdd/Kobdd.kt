package org.kobdd

import org.kobdd.Kobdd.Companion.FALSE
import org.kobdd.Kobdd.Companion.ONE_NODE
import org.kobdd.Kobdd.Companion.TRUE
import org.kobdd.Kobdd.Companion.ZERO_NODE
import org.kobdd.Kobdd.Companion.mkNode
import org.kobdd.Kobdd.Companion.one
import org.kobdd.Kobdd.Companion.opcache
import org.kobdd.Kobdd.Companion.variable
import org.kobdd.Kobdd.Companion.zero
import java.lang.ref.WeakReference
import kotlin.math.absoluteValue

class Kobdd private constructor(val node: Int) {

    //overriding methods
    override fun equals(other: Any?): Boolean = other is Kobdd && node == other.node
    override fun hashCode(): Int = node
    override fun toString(): String = if (node == ONE_NODE) "KOBDD(TRUE)" else if (node == ZERO_NODE) "KOBDD(FALSE)" else  "KOBDD(node=$node, var=${variable(node)})"

    companion object {
        /**
         * Fast hashtable implementation without reference types. [TERM_MARKER] is like <null> for classic linked lists.
         */
        private const val TERM_MARKER = -1

        /**
         * Length of [buckets] array and number of nodes in [storage] array. Each node is 4 ints, so [storage].size = [capacity]*4
         * Use only powers of 2 for capacity because ([capacity]-1) gives us handful bitmap for hashcode() to bucketNumber mapping
         */
        private var capacity = 1 shl 10 //will use only powers of 2 as size

        /**
         * Number of nodes in storage. Invariant: [size] <= [capacity]
         */
        internal var size: Int = 0

        /**
         * The same as buckets in classic hashtable but stores index of node in [storage] instead of pointer
         */
        private var buckets = IntArray(capacity) { TERM_MARKER }

        /**
         * Storage for all BDSs nodes.  Each node is 4 32bit ints [variable | oneBranchIndex | zeroBranchIndex | nextNodeInBucketIndex]
         * So [storage].size = [capacity]*4
         */
        private var storage = IntArray(capacity  shl 2)

        internal fun variable(index: Int) : Int = storage[index shl 2]
        internal fun one(index: Int) : Int = storage[(index shl 2) + 1]
        internal fun zero(index: Int) : Int = storage[(index shl 2) + 2]
        private fun insertNode(variable: Int, one: Int, zero: Int): Int {
            val index = size ++
//            if (index % 1_000_000 == 0)
//                println("Nodes created: $index")
            storage[index shl 2] = variable
            storage[(index shl 2)+1] = one
            storage[(index shl 2)+2] = zero
            storage[(index shl 2)+3] = TERM_MARKER
            return index
        }

        private fun next(index: Int) : Int = storage[(index shl 2) + 3]
        private fun setNext(index: Int, nextNodeIndex: Int) {
            storage[(index shl 2) + 3] = nextNodeIndex
        }

        //'TRUE' node
        const val ONE_NODE = -2

        //'FALSE' node
        const val ZERO_NODE = -3

        private fun hash(variable: Int, one: Int, zero: Int) : Int {
            //TODO not sure whether it's good hash code for 3 integers
            return variable * 31 * 31 + one * 31 + zero
        }

        const val MAX_CAPACITY_BITS = 26
        const val MAX_CAPACITY = 1 shl MAX_CAPACITY_BITS

        private fun ensureCapacity() {
            assert(size <= capacity)
            if (size < capacity) return

            //TODO Need to implement Garbage collection based on WeakReferences and [ref] at this codepoint
            require(capacity < MAX_CAPACITY) {"Maximum capacity $MAX_CAPACITY is reached, can't use more memory"}
            capacity = capacity shl 1
            val bitmask = capacity - 1

            buckets = IntArray(capacity) { TERM_MARKER }
            storage = storage.copyInto(IntArray(capacity shl 2), 0, 0, size shl 2)
            for (storageIndex in 0 until size) {
                val bucketIndex = hash(variable(storageIndex), one(storageIndex), zero(storageIndex)) and bitmask
                val head = buckets[bucketIndex]
                buckets[bucketIndex] = storageIndex
                setNext(storageIndex, head)
            }


        }

        //Dangerous, all BDD becomes invalid
        fun reset() {
            opcache.clear()
            buckets.fill(TERM_MARKER)
            size = 0
        }

        //index of node, or -1
        internal fun mkNode(variable: Int, one: Int, zero: Int): Int {
            assert(variable > 0)
            assert(one in 0 until size || one == ONE_NODE || one == ZERO_NODE)
            assert(zero in 0 until size || zero == ONE_NODE || zero == ZERO_NODE)

            if (one == zero)
                return one //BDD optimization

            ensureCapacity() //in fact this is needed only when 'size' increases, but it's harder

            val bitmask = capacity - 1
            val bucketIndex = hash(variable, one, zero) and bitmask
            var storageIndex = buckets[bucketIndex]
            if (storageIndex == TERM_MARKER) {
                return insertNode(variable, one, zero).also { buckets[bucketIndex] = it }
            }
            while (true) {
                if (variable(storageIndex) == variable && one(storageIndex) == one && zero(storageIndex) == zero)
                    return storageIndex //already existing node

                if (next(storageIndex) == TERM_MARKER) {
                    return insertNode(variable, one, zero).also { setNext(storageIndex, it) }
                } else {
                    storageIndex = next(storageIndex)
                }

            }
        }

        internal val refs = mutableListOf<WeakReference<Kobdd>>()

        val TRUE : Kobdd get() = Kobdd(ONE_NODE)
        val FALSE : Kobdd get() = Kobdd(ZERO_NODE)


        internal val opcache = Opcache()

        internal operator fun invoke(node: Int) =
                when (node) {
                    ONE_NODE -> TRUE
                    ZERO_NODE -> FALSE
                    else -> Kobdd(node)
                }


    }

    init {
        refs.add(WeakReference(this))
    }

}


/**
 * Literal is either a variable number in [1..Int.MAX_VALUE] or it's negation
 */
fun clause(literal: Int) : Kobdd {
    require(literal != 0) {"0 is not allowed for literal"}
    require(literal != Int.MIN_VALUE) {"Int.MIN_VALUE is not allowed for literal"}
    return Kobdd(
            if (literal > 0)
                mkNode(literal, ONE_NODE, ZERO_NODE)
            else
                mkNode(-literal, ZERO_NODE, ONE_NODE)
    )
}

enum class ClauseKind(val emptyClauseNode: Int, val literalWithItsNegation: Kobdd) {
    Conjunction(ONE_NODE, FALSE),
    Disjunction(ZERO_NODE, TRUE);
}

/**
 * Creates clause (e.g. disjunction, conjunction depending on [ClauseKind]) from [literals].
 * Order of literals is no matter.
 *
 * Example: `clause(Disjunction, [-3, 1, 2])` is BDD representing formula `x₁ ∨ x₂ ∨ ¬x₃`
 */
fun clause(clauseKind: ClauseKind, literals: List<Int>) : Kobdd {
    val sortedLiterals = literals.sortedByDescending { it.absoluteValue }

    var currentNode = clauseKind.emptyClauseNode
    for (i in sortedLiterals.indices) {
        if (i > 0) { //check for consequent 'x' 'x' and '-x' 'x'
            if (sortedLiterals[i] == sortedLiterals[i - 1])
                continue
            if (sortedLiterals[i] == -sortedLiterals[i-1])
                return clauseKind.literalWithItsNegation
        }
        val literal = sortedLiterals[i]
        require(literal != 0) {"0 is not allowed for literal"}
        require(literal != Int.MIN_VALUE) {"Int.MIN_VALUE is not allowed for literal"}

        currentNode = when (clauseKind) {
            ClauseKind.Conjunction ->
                if (literal > 0)
                    mkNode(literal, currentNode, ZERO_NODE)
                else
                    mkNode(-literal, ZERO_NODE, currentNode)

            ClauseKind.Disjunction ->
                if (literal > 0)
                    mkNode(literal, ONE_NODE, currentNode)
                else
                    mkNode(-literal, currentNode, ONE_NODE)
        }
    }

    return Kobdd(currentNode)
}



/**
 * Substitution of variable's value info formula represented by BDD. F[abs([variableWithValue])=sign([variableWithValue])]
 */
fun Kobdd.substitute(variableWithValue: Int): Kobdd {
    require(variableWithValue != 0) {"0 is not allowed for literal"}
    require(variableWithValue != Int.MIN_VALUE) {"Int.MIN_VALUE is not allowed for literal"}

    fun mkSubst(node: Int, literal: Int) : Int {
        val v = literal.absoluteValue
        return when {
            v < variable(node) -> node
            v == variable(node) -> if (literal > 0) one(node) else zero(node)
            else /* v > variable(node) */ -> mkNode(variable(node), mkSubst(one(node), literal), mkSubst(zero(node), literal))
        }
    }

    return Kobdd(mkSubst(node, variableWithValue))
}


fun Kobdd.substituteInterpretation(partialInterpretation: IntArray): Kobdd {

    val cache = opcache.createCacheForUnaryOp()

    fun mkSubstituteInterpretation(node: Int) : Int{
        if (node < 0) //TERMINAL
            return node

        val v = variable(node)
        return cache.getOrPut(node) {
            when {
                //doesn't substitute anything for this node, because we don't know abything about it
                partialInterpretation[v] == 0 -> mkNode(v, mkSubstituteInterpretation(one(node)), mkSubstituteInterpretation(zero(node)))

                partialInterpretation[v] > 0 -> {
                    mkSubstituteInterpretation(one(node))
                }
                else -> {
                    mkSubstituteInterpretation(zero(node))
                }
            }

        }
    }
    return Kobdd(mkSubstituteInterpretation(node))
}

/**
 * Creates conjunction of literals
 */
fun conj(vararg literals: Int) = clause(ClauseKind.Conjunction, literals.toList())

/**
 * Creates disjunction of literals.
 */
fun disj(vararg literals: Int) = clause(ClauseKind.Disjunction, literals.toList())


private val negateOpcache = opcache.createCacheForUnaryOp()
/**
 * Negates formula represented by [this] BDD.
 */
fun Kobdd.negate() : Kobdd {
    fun mkNegate(node: Int) : Int {
        if (node == ONE_NODE) return ZERO_NODE
        if (node == ZERO_NODE) return ONE_NODE


        return negateOpcache.getOrPut(node) { mkNode(variable(node), mkNegate(one(node)), mkNegate(zero(node))) }
    }
    return Kobdd(mkNegate(node))
}

private fun mkAnd(left: Int, right: Int) : Int {
    if (left == right) return left
    if (left == ZERO_NODE || right == ZERO_NODE) return ZERO_NODE
    if (left == ONE_NODE) return right
    if (right == ONE_NODE) return left

    return opcache.binop(BinopKind.And, left, right) {
        when {
            variable(left) < variable(right) -> mkNode(variable(left), mkAnd(one(left), right), mkAnd(zero(left), right))
            variable(left) > variable(right) -> mkNode(variable(right), mkAnd(one(right), left), mkAnd(zero(right), left))
            else -> mkNode(variable(left), mkAnd(one(left), one(right)), mkAnd(zero(left), zero(right)))
        }
    }
}

private fun mkOr(left: Int, right: Int) : Int {
    if (left == right) return left
    if (left == ONE_NODE || right == ONE_NODE) return ONE_NODE
    if (left == ZERO_NODE) return right
    if (right == ZERO_NODE) return left

    return opcache.binop(BinopKind.Or, left, right) {
      when {
          variable(left) < variable(right) -> mkNode(variable(left), mkOr(one(left), right), mkOr(zero(left), right))
          variable(left) > variable(right) -> mkNode(variable(right), mkOr(one(right), left), mkOr(zero(right), left))
          else -> mkNode(variable(left), mkOr(one(left), one(right)), mkOr(zero(left), zero(right)))
      }
    }
}

/**
 * Conjunction of formulas: [this] & [other]
 */
fun Kobdd.and(other: Kobdd) : Kobdd = Kobdd(mkAnd(this.node, other.node))

/**
 * Disjunction of formulas: [this] | [other]
 */
fun Kobdd.or(other: Kobdd) : Kobdd = Kobdd(mkOr(this.node, other.node))


fun Kobdd.exists(variable: Int) : Kobdd {
    val existsOpcache = opcache.createCacheForUnaryOp()

    fun mkExists(node: Int, variable: Int) : Int {
        if (node == ONE_NODE || node == ZERO_NODE) return node

        return existsOpcache.getOrPut(node) {
            when {
                variable < variable(node) -> node
                variable > variable(node) -> mkNode(variable(node), mkExists(one(node), variable), mkExists(zero(node), variable))
                else -> mkOr(one(node), zero(node))
            }
        }
    }

    require(variable > 0) { "Variable must be greater that 0, but it's $variable" }
    return Kobdd(mkExists(node, variable))
}

fun Kobdd.isSat() = node != ZERO_NODE

/**
 * Return list of literals (positive if var=true, negative otherwise)
 * that satisfy formula or null if impossible.
 * If returned list is empty, it means formula is equals to TRUE, so and variables will satisfy it.
 */
fun Kobdd.model() : List<Int>? {
    if (node == ZERO_NODE) return null
    val res = mutableListOf<Int>()
    var curNode = node
    while (curNode != ONE_NODE) {
        if (one(curNode) != ZERO_NODE) {
            res.add(variable(curNode))
            curNode = one(curNode)
        } else {
            res.add(-variable(curNode))
            curNode = zero(curNode)
        }
    }
    return res
}

internal fun Kobdd.refineInterpretation(partialInterpretation: IntArray) {
    var curNode = node
    while (curNode != ONE_NODE) {
        assert (curNode != ZERO_NODE)

        //we mustn't invoke this method for bdd containing variables that already assigned in interpretation
        assert(partialInterpretation[variable(curNode)] == 0) {
            "Method mustn't be called for bdd with variables that already assigned. Variable=${variable(curNode)}"
        }

        if (one(curNode) != ZERO_NODE) {
            partialInterpretation[variable(curNode)] = 1
            curNode = one(curNode)
        } else {
            partialInterpretation[variable(curNode)] = -1
            curNode = zero(curNode)
        }

    }
}

operator fun Kobdd.unaryMinus() = negate()
operator fun Kobdd.plus(other: Kobdd) = this.or(other)
operator fun Kobdd.times(other: Kobdd) = this.and(other)