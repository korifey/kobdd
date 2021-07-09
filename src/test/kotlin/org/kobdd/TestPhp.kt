package org.kobdd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.system.measureNanoTime

class TestPhp {

    @Test
    fun testPhp() {
        for (holes in 1..100) {
            Kobdd.reset()
            val time = measureNanoTime {
                php(holes)
            }
            println("Solved in "+"%.2f".format(time / 1000_000_000.0)+" sec")
            println("BDD nodes: ${Kobdd.size}")
        }
    }

    private fun php(holes: Int) {
        println()
        val pingeons = holes + 1
        val clauses = generatePhpClauses(pingeons, holes)
        val slice = pingeons * (pingeons - 1) / 2

        assertEquals(pingeons + holes * slice, clauses.size)
        println("PHP($pingeons,$holes): #clauses=${clauses.size}")

        fun v(i: Int, j: Int) = (i - 1) * holes + j
        //            val res = solveCnf(holes * pingeons, clauses, ::allProjection)
        val res = solveCnf(holes * pingeons, clauses) { vars, nodes ->
            var acc = join(nodes.take(pingeons))
    //                println(acc)
            var next = pingeons

            for (j in 1..holes) {
                val b = join(nodes.subList(next, next + slice))
    //                    println(b)
                acc = Join(acc, b)
                next += slice
                for (i in 1..pingeons)
                    acc = Projection(v(i, j), acc)

    //                    println(acc)
//                println("#clauses processed: $next")
            }

            assert(next == clauses.size)
            acc
        }
        if (res != null) println("SAT")
        else println("UNSAT")
    }
}