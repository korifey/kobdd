package org.kobdd

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import kotlin.system.measureNanoTime

typealias Clauses = Array<out List<Int>>

sealed class ProofNode(val bdd: Kobdd, private val children: List<ProofNode>, val hasProjection: Boolean) {
    open fun populateModel(model: IntArray) {
        bdd.populateModel(model)
        for (child in children) {
            if (child.hasProjection)
                child.populateModel(model)
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
    override fun toString() = "∃$variable($node)"
}

fun solveCnf(vars: Int, clauses: Clauses, strategy: (vars: Int, initial: List<Axiom>) -> ProofNode): List<Int>? {
    if (clauses.isEmpty()) return listOf() //always SAT

    val nodes = clauses.map { Axiom(it) }

    val root = strategy(vars, nodes)
    if (!root.bdd.isSat()) return null //no solution

    val model = IntArray(vars+1)
    root.populateModel(model)

    val res = mutableListOf<Int>()
    for (v in model.indices) {
        if (model[v] > 0) res.add(v)
        if (model[v] < 0) res.add(-v)
        //zero mean variable doesn't influence on sat
    }

    //TODO remove it: here we just check that we are correct
    checkSolutionCnf(res, clauses)

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

fun join(nodes: List<ProofNode>) = nodes.fold(Empty as ProofNode) { acc, node-> Join(acc, node)}
fun allJoinStrategy(vars: Int, nodes: List<Axiom>) = join(nodes)
fun allProjection(vars: Int, nodes: List<Axiom>) : ProofNode {
    val unused = nodes.toMutableSet()
    return (1..vars).fold(Empty as ProofNode) { acc, v ->
        val toJoin = unused.filter { it.clause.contains(v) || it.clause.contains(-v) }
        unused.removeAll(toJoin)
        Projection(v, Join(acc, join(toJoin)))
    }
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
            model = solveCnf(vars, clauses, ::allJoinStrategy)
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
