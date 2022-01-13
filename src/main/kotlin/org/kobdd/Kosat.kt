package org.kobdd

import org.kobdd.util.PersistentBoolset
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import kotlin.math.log2
import kotlin.system.measureNanoTime

typealias Clauses = Array<out List<Int>>

sealed class ProofNode(val bdd: Kobdd, private val children: List<ProofNode>, val hasProjection: Boolean, val vars: PersistentBoolset) {
    val nodeCount = bdd.nodeCount()

    open fun refineInterpretation(partialInterpretation: IntArray) {
        val substituted = bdd.substituteInterpretation(partialInterpretation)
        substituted.refineInterpretation(partialInterpretation)

        for (child in children) {
            if (child.hasProjection)
                child.refineInterpretation(partialInterpretation)
        }
    }
}

object Empty : ProofNode(Kobdd.TRUE, listOf(), false, PersistentBoolset()) {
    override fun toString() = "T"
}

class Axiom(val clause: List<Int>) : ProofNode(clause(ClauseKind.Disjunction, clause), listOf(), false, PersistentBoolset(clause)) {
    override fun toString(): String = clause.joinToString ("|")
}
class Join(val left: ProofNode, val right:ProofNode) :
        ProofNode(left.bdd * right.bdd, listOf(left, right), left.hasProjection or right.hasProjection, left.vars + right.vars) {

    override fun toString(): String = if (left != Empty) "$left & $right" else right.toString()
}
class Projection(val variable: Int, val node: ProofNode) : ProofNode(node.bdd.exists(variable), listOf(node), true, node.vars - variable) {
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

    val interpretation = IntArray(vars+1)
    root.refineInterpretation(interpretation)

    val res = interpretation.toModel()

    //TODO remove it: here we just check that we are correct
    require(checkSolutionCnf(interpretation, clauses))

    return res
}

fun IntArray.toModel(): List<Int> {
    val res = mutableListOf<Int>()
    for (v in this.indices) {
        if (this[v] > 0) res.add(v)
        if (this[v] < 0) res.add(-v)
        //zero mean variable doesn't influence on sat
    }
    return res
}

fun List<Int>.toInterpretation(vars: Int) : IntArray {
    val res = IntArray(vars+1)
    forEach { if (it>0) res[it] = 1 else res[-it] = -1 }
    return res
}


fun checkSolutionCnf(interpretation: IntArray, clauses: Array<out List<Int>>) : Boolean {
    outer@for (clause in clauses) {
        for (v in clause) {
            if (v > 0 && interpretation[v] > 0) continue@outer
            if (v < 0 && interpretation[-v] < 0) continue@outer
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
//        println("Var: $v")
        val toJoin = unused.filter { it.clause.contains(v) || it.clause.contains(-v) }
        if (!toJoin.isEmpty()) {
            unused.removeAll(toJoin)
            Projection(v, Join(acc, join(toJoin)))
        } else
            acc

    }
}


fun allProjectionSortedByUnusedAsc(vars: Int, nodes: List<Axiom>) : ProofNode {
    val proofNodes = nodes.toMutableSet<ProofNode>()

    val vEstimate = (1..vars).associate { v -> v to proofNodes.sumOf { n ->
        if (n.vars.contains(v))
            log2(n.nodeCount.toDouble())
        else 0.0
    }
    }.toMutableMap()

    while (proofNodes.size > 1) {
        val nextVar = vEstimate.minByOrNull { (_, estimate) -> estimate }!!.key

        val toJoin = proofNodes.filter { it.vars.contains(nextVar) }
        proofNodes.removeAll(toJoin)

        for (n in toJoin) {
            for (v in n.vars.toList()) {
                vEstimate[v] = vEstimate.getValue(v) - log2(n.nodeCount.toDouble())
            }
        }

        val newNode = Projection(nextVar, join(toJoin))
        proofNodes.add(newNode)
        for (v in newNode.vars.toList()) {
            vEstimate[v] = vEstimate.getValue(v) + log2(newNode.nodeCount.toDouble())
        }
        vEstimate.remove(nextVar)
    }

    return proofNodes.single()
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

        val vStat = Array(vars+1) {v -> v to clauses.count { it.contains(v) || it.contains(-v) }}
        vStat.sortBy { (v, cnt) -> cnt}
        for ((v, cnt) in vStat) {
            if (v == 0) continue
            println("$v: $cnt")
        }

        measureNanoTime {
            model = solveCnf(vars, clauses, ::allProjectionSortedByUnusedAsc)
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
    println("v KOBDD SAT SOLVER, v1.0")
    println("v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html")

    processCnfRequests(readCnfRequests(System.`in`))
}
