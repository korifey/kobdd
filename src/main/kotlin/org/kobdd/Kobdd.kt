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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.absoluteValue
import kotlin.system.measureNanoTime

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
        private var size: Int = 0

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

fun conj(vararg literals: Int) = clause(ClauseKind.Conjunction, literals.toList())

/**
 * Creates disjunction of literals.
 */
fun disj(vararg literals: Int) = clause(ClauseKind.Disjunction, literals.toList())

private fun mkNegate(node: Int) : Int {
    if (node == ONE_NODE) return ZERO_NODE
    if (node == ZERO_NODE) return ONE_NODE


    return mkNode(variable(node), mkNegate(one(node)), mkNegate(zero(node)))
}

/**
 * Negates formula represented by [this] BDD.
 */
fun Kobdd.negate() : Kobdd = Kobdd(mkNegate(node))

//TODO use cache instead hashtable
//val andCache = hashMapOf<Pair<Int, Int>, Int>()
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

//TODO use cache instead hashtable
//val orCache = hashMapOf<Pair<Int, Int>, Int>()
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


val existsCache = hashMapOf<Int, Int>()
fun mkExists(node: Int, variable: Int) : Int {
    if (node == ONE_NODE || node == ZERO_NODE) return node
    return existsCache.getOrPut(node, { when {
        variable < variable(node) -> node
        variable > variable(node) -> mkNode(variable(node), mkExists(one(node), variable), mkExists(zero(node), variable))
        else -> mkOr(one(node), zero(node))
    }})
}

fun Kobdd.exists(variable: Int) : Kobdd {
    require(variable > 0) { "Variable must be greater that 0, but it's $variable" }

    existsCache.clear()
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

operator fun Kobdd.unaryMinus() = negate()
operator fun Kobdd.plus(other: Kobdd) = this.or(other)
operator fun Kobdd.times(other: Kobdd) = this.and(other)


fun processCnfRequest(vars: Int, clauses: Array<MutableList<Int>>) : List<Int>?{

//1. stupid strategy without using `exists()`, stop working at http://user.it.uu.se/~tjawe125/software/pigeonhole/pigeon-12.cnf
    var resBdd = TRUE
    for (c in clauses) {
        resBdd *= clause(ClauseKind.Disjunction, c)
        println("v processed clause $c")
    }
    return resBdd.model()


//    2. random var strategy
//    var bdd = TRUE
//
//    val unusedClauses = clauses.toMutableSet()
//    val variables = mutableSetOf<Int>()
//    while (unusedClauses.isNotEmpty()) {
//        //take some clause
//        val clause = unusedClauses.iterator().next()
//        unusedClauses.remove(clause)
//
//        variables.addAll(clause.map { it.absoluteValue })
//        bdd *= clause(ClauseKind.Disjunction, clause)
//
//        while (variables.isNotEmpty()) {
//            val v = variables.iterator().next()
//
//            for (cc in unusedClauses.filter { it.contains(v) || it.contains(-v) }) {
//                println("v processed clause $cc: (${clauses.size - unusedClauses.size} of ${clauses.size})")
//                unusedClauses.remove(cc)
//                bdd *= clause(ClauseKind.Disjunction, cc)
//                variables.addAll(cc.map { it.absoluteValue })
//            }
//            bdd = bdd.exists(v)
//            variables.remove(v)
//            if (bdd == FALSE) return null //shortcut
//        }
//    }
//
//    return if (bdd == FALSE) null
//    else listOf() //TODO no actual model, just SAT in this case
}

fun main() {
    println("v KOBDD SAT SOLVER, v0.1")
    println("v Solves formulas in CNF form as specified in http://www.satcompetition.org/2004/format-solvers2004.html")

    val reader = BufferedReader(InputStreamReader(System.`in`))
    val scanner = Scanner(reader)

    while (scanner.hasNext()) {
        val token = scanner.next()

        if (token == "c") {
            scanner.nextLine()
            continue
        } //skip comment

        if (token != "p")
            error ("Illegal token $token. Only 'c' and 'p' command are supported")

        val cnf = scanner.next()
        if (cnf != "cnf")
            error ("Illegal request $cnf. Only 'cnf' supported")

        val vars = scanner.nextInt() //don't need this variable
        val clauses = Array(scanner.nextInt()) { mutableListOf<Int>()}
        for (i in clauses.indices) {
            while (true) {
                val nxt = scanner.nextInt()

                if (nxt == 0) break
                else clauses[i].add(nxt)
            }
        }

        val model: List<Int>?
        println("v Start processing CNF request with $vars variables and ${clauses.size} clauses")
        val time = measureNanoTime {
            model = processCnfRequest(vars, clauses)
        }
        println("v Request processed in %.3f sec".format(time / 1_000_000_000.0))

        if (model == null) {
            println("s UNSATISFIABLE")
            continue
        }

        println("s SATISFIABLE")
        if (model.isEmpty())
            println("c Done: any solution satisfies formula. ")
        else {
            println("v " + model.joinToString(" "))
            println("c Done")
        }
    }
}
