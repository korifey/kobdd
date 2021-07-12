package org.kobdd

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import kotlin.system.measureNanoTime

typealias Clauses = Array<out List<Int>>

sealed class ProofNode(val bdd: Kobdd, private val children: List<ProofNode>, val hasProjection: Boolean) {
    open fun refineInterpretation(partialInterpretation: IntArray) {
        val substituted = bdd.substituteInterpretation(partialInterpretation)
        substituted.refineInterpretation(partialInterpretation)

        for (child in children) {
            if (child.hasProjection)
                child.refineInterpretation(partialInterpretation)
        }
    }
}

object Empty : ProofNode(Kobdd.TRUE, listOf(), false) {
    override fun toString() = "T"
}
class Axiom(val clause: List<Int>) : ProofNode(clause(ClauseKind.Disjunction, clause), listOf(), false) {
    override fun toString(): String = clause.joinToString ("|")
}
class Join(val left: ProofNode, val right:ProofNode) : ProofNode(left.bdd * right.bdd, listOf(left, right), left.hasProjection or right.hasProjection) {
    override fun toString(): String = if (left != Empty) "$left & $right" else right.toString()
}
class Projection(val variable: Int, val node: ProofNode) : ProofNode(node.bdd.exists(variable), listOf(node), true) {
    override fun refineInterpretation(partialInterpretation: IntArray) {
        node.refineInterpretation(partialInterpretation)
    }

    override fun toString() = "∃$variable($node)"
}

fun solveCnf(vars: Int, clauses: Clauses, strategy: (vars: Int, initial: List<Axiom>) -> ProofNode): List<Int>? {
    if (clauses.isEmpty()) return listOf() //always SAT

    val nodes = clauses.map { Axiom(it) }

    val root = strategy(vars, nodes)
    if (!root.bdd.isSat()) return null //no solution

    val model = IntArray(vars+1)
    root.refineInterpretation(model)

    val res = model.toInterpretation()

    //TODO remove it: here we just check that we are correct
    require(checkSolutionCnf(res, clauses))

    return res
}

private fun IntArray.toInterpretation(): List<Int> {
    val res = mutableListOf<Int>()
    for (v in this.indices) {
        if (this[v] > 0) res.add(v)
        if (this[v] < 0) res.add(-v)
        //zero mean variable doesn't influence on sat
    }
    return res
}

private fun checkSolutionCnf(interpretation: List<Int>, clauses: Array<out List<Int>>) : Boolean {
    //we hope that model is sorted by abs values
    val setOtTrue = BooleanArray((interpretation.maxOrNull()?:0)+1)
    interpretation.forEach {if (it>0) setOtTrue[it] = true }

    outer@for (clause in clauses) {
        for (v in clause) {
            if (v > 0 && setOtTrue.size > v && setOtTrue[v]) continue@outer
            if (v < 0 && (setOtTrue.size <= -v || !setOtTrue[-v])) continue@outer
        }
        //disjunction is false
        return false
    }
    return true
}

/**
 * Create [Join] of [nodes] sequentially left-to-right.
 */
fun join(nodes: List<ProofNode>) = nodes.fold(Empty as ProofNode) { acc, node-> Join(acc, node)}

/**
 * Strategy for [solveCnf] that [join]s every clause with conjunction sequentially without using [Projection]
 */
fun allJoinStrategy(vars: Int, nodes: List<Axiom>) = join(nodes)

/**
 * Strategy for [solveCnf] that for each variable `v` from [vars]:
 * - [join]s root [ProofNode] `f` with every clause that contains `v`
 * - Do [Projection] of root [ProofNode] `f` with `v`, i.e. ∃v.f
 */
fun allProjectionStrategy(vars: Int, nodes: List<Axiom>) : ProofNode {
    val unused = nodes.toMutableSet()
    return (1..vars).fold(Empty as ProofNode) { acc, v ->
        val toJoin = unused.filter { it.clause.contains(v) || it.clause.contains(-v) }
        if (!toJoin.isEmpty()) {
            unused.removeAll(toJoin)
            Projection(v, Join(acc, join(toJoin)))
        } else
            acc

    }
}

data class CnfRequest(val vars: Int, val clauses: Array<out List<Int>>)

/**
 * Reads [CnfRequest]'s assuming [stream] is formatted according [Simplified DIMACS](http://www.satcompetition.org/2004/format-solvers2004.html)
 */
fun readCnfRequests(stream: InputStream) = sequence {
    val reader = BufferedReader(InputStreamReader(stream))
    val scanner = Scanner(reader)

    while (scanner.hasNext()) {
        val token = scanner.next()

        if (token == "c") {
            scanner.nextLine()
            continue
        } //skip comment

        if (token == "%") {
            break
        }

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

        yield(CnfRequest(vars, clauses))
    }
}

fun processCnfRequests(requests: Sequence<CnfRequest>) {
    for ((vars, clauses) in requests) {
        val model: List<Int>?
        println("v Start processing CNF request with $vars variables and ${clauses.size} clauses")
        measureNanoTime {
            model = solveCnf(vars, clauses, ::allProjectionStrategy)
        }.let { time -> println("v Request processed in %.3f sec".format(time / 1_000_000_000.0)) }

        if (model == null) {
            println("s UNSATISFIABLE")
            continue
        }

        println("s SATISFIABLE")
        if (model.isEmpty())
            println("c Done: formula is tautology. Any solution satisfies it.")
        else {
            println("v " + model.joinToString(" "))
            println("c Done")
        }
    }
}

fun main() {
    println("v KOBDD SAT SOLVER, v0.1")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")


    processCnfRequests(readCnfRequests(System.`in`))
}
