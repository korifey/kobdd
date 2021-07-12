package org.kobdd

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.system.measureNanoTime

class TestQueens {

    companion object {
        @JvmStatic
        fun range() = (1..10).toList().toTypedArray()
    }
    @MethodSource("range")
    @ParameterizedTest
    fun testQueens(q: Int) {
        Kobdd.reset()
        val time = measureNanoTime {
            queens(q)
        }
        println("Solved in "+"%.3f".format(time / 1000_000_000.0)+" sec")
        println("BDD nodes: %,d".format(Kobdd.size))
    }

    private fun queens(n: Int) {
        println()


        val clauses = mutableListOf<List<Int>>()
        fun v(i: Int, j: Int) = (i * n + j) + 1
        for (i in 0 until n) {
            //horizontal
            clauses.addAll(popcnt1Cnf((0 until n).map { j -> v(i, j) }))
            //Vertical
            clauses.addAll(popcnt1Cnf((0 until n).map { j -> v(j, i) }))

            //left-top-to-right-bottom diag
            clauses.addAll(atMost1Cnf((0 until (n-i)).map { j -> v(j, i+j) }))
            if (i!= 0) clauses.addAll(atMost1Cnf((0 until (n-i)).map { j -> v(i+j, j) }))

            //left-bottom-to-right-top diag
            clauses.addAll(atMost1Cnf((0 until (n-i)).map { j -> v(n-1-j, i+j) }))
            if (i!= 0) clauses.addAll(atMost1Cnf((0 until (n-i)).map { j -> v(n-1-i-j, j) }))
        }
        println("Queens($n), #clausess=${clauses.size}")

        val vars = n*n
        val model = solveCnf(vars, clauses.toTypedArray(), ::allJoinStrategy)
        if (model == null) {
            println("UNSAT")
            return
        }

        println("SAT")

        val x = model.toInterpretation(vars)
        val str = (0 until n).joinToString("\n"+"-+".repeat(n-1)+"-\n") { i ->
            (0 until n).joinToString("|") { j ->
                if (x[v(i,j)] > 0) "x" else " "
            }
        }
        println(str)
    }
}