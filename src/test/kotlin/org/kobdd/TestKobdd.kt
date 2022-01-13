package org.kobdd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test


class TestKobdd {

    @Test
    fun testClause() {
        assertEquals(listOf(1, -2), conj(1, -2).model())
        assertEquals(null, conj(1, -1).model())

        assertEquals(listOf(1), disj(1, -2).model())
        assertEquals(listOf<Int>(), disj(1, -1).model())

        assertEquals(listOf(1, 3, 5, 7), conj(1, 2, 3, 4, 5, 6, 7, 8)
                .exists(2)
                .exists(4)
                .exists(6)
                .exists(8)
                .model()
        )
    }

    @Test
    fun testPrintClause() {
        val cnf = arrayOf(
            listOf(1, 2, -3),
            listOf(-1, -2, 3),
            listOf(-1, -3),
            listOf(-2, -3)
        )

        println("Test output can be viewed in terminal, not in IDE")
        println("Solving CNF: ${printCnfUnicode(cnf)}")
        val solution = solveCnf(3, cnf, ::allJoinStrategy)
        assertNotNull(solution)
        println("Solution: ${printClauseUnicode(ClauseKind.Conjunction, solution!!)}")
    }
}