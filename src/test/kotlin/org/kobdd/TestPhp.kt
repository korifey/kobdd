package org.kobdd

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.system.measureNanoTime

class TestPhp {

    /**
     * Encoding according
     * [A direct construction of polynomial-size OBDD proof of pigeon hole problem, Wei Chén a,b,∗, Wenhui Zhang a]
     */
    private fun generatePhpClauses(pigeons : Int, holes: Int) : Clauses {
        require(pigeons > 0)
        require(holes > 0)

        //p * h variables: let's encode it
        fun v(i: Int, j: Int) = (i-1)*holes+j

        val res  = mutableListOf<List<Int>>()
        // A: p clauses
        for (i in 1..pigeons)
            res.add((1..holes).map { j -> v(i,j) })

        // B[j] clauses
        for (j in 1..holes) //h *
            for (i in 1..pigeons) //(p-1) * p / 2
                for (k in i+1..pigeons)
                    res.add(listOf(-v(i,j), -v(k, j)))

        return res.toTypedArray()
    }


    companion object {
        @JvmStatic
        fun range() = (1..90).toList().toTypedArray()

        @BeforeAll
        @JvmStatic
        fun before() {
            //md format for readme
            println("Problem | #clauses | #nodes | time ")
            println("--- | --- | --- | ---")

            //warmup
            TestPhp().php(1)
        }
    }

    @MethodSource("range")
    @ParameterizedTest
    fun testPhp(holes: Int) {
        Kobdd.reset()
        val clauses : Int
        val time = measureNanoTime {
            clauses = php(holes)
        }
        println("PHP(${holes+1}, ${holes}) | ${clauses.formatMd()} | ${Kobdd.size.formatMd()} | " + "%.3f".format(time / 1000_000_000.0)+" sec")
    }
    private fun Int.formatMd() = "%,d".format(this)

    private fun php(holes: Int) : Int {
        val pigeons = holes + 1
        val clauses = generatePhpClauses(pigeons, holes)
        val slice = pigeons * (pigeons - 1) / 2

        assertEquals(pigeons + holes * slice, clauses.size)

        fun v(i: Int, j: Int) = (i - 1) * holes + j

//      Uncomment this to ty solve PHP with "Join all strategy" - without Projections - fails on [holes=13]
//      val res = solveCnf(holes * pingeons, clauses, ::allJoinStrategy)

//      Uncomment this to ty solve PHP with "Project all strategy" - fails on [holes=13]
//      val res = solveCnf(holes * pingeons, clauses, ::allProjectionStrategy)

        val res = solveCnf(holes * pigeons, clauses) { vars, nodes ->
            var acc = join(nodes.take(pigeons))
            var next = pigeons

            for (j in 1..holes) {
                val b = join(nodes.subList(next, next + slice))
                acc = Join(acc, b)
                next += slice
                for (i in 1..pigeons)
                    acc = Projection(v(i, j), acc)

            }

            assert(next == clauses.size)
            acc
        }
        assertNull(res)
        return clauses.size
    }
}