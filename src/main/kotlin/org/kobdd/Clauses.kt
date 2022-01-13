package org.kobdd

import kotlin.math.absoluteValue

fun popcnt1Cnf(vars: List<Int>) : List<List<Int>> {
    val res = mutableListOf(vars)
    for (i in vars.indices)
        for (j in (i+1) until vars.size)
            res.add(listOf(-vars[i], -vars[j]))
    return res
}

fun atMost1Cnf(vars: List<Int>) : List<List<Int>> {
    val res = mutableListOf<List<Int>>()
    for (i in vars.indices)
        for (j in (i+1) until vars.size)
            res.add(listOf(-vars[i], -vars[j]))
    return res
}

fun printLiteralUnicode(literal: Int) : String {
    val base = "\ud835\udc65"+ // Mathematical x
            if (literal < 0) "\u0305" else "" //underscore
    val subscript = literal.absoluteValue.toString().map {
        ((0x20 shl 8) + 0x80 + (it - '0')).toChar() //subscript char
    }
    return base + subscript.joinToString("")
}

fun printClauseUnicode(kind: ClauseKind, clause: List<Int>, literal: (Int) -> String = ::printLiteralUnicode) : String {
    if (clause.isEmpty()) return "\u22A5" //false
    else return clause.joinToString(kind.joinString) { literal(it)}
}

fun printCnfUnicode(cnfClauses: Array<List<Int>>, multiline : Boolean = false) : String {
    if (cnfClauses.isEmpty()) return "\u22A4" //true
    return cnfClauses.joinToString(ClauseKind.Conjunction.joinString + if (multiline) "\n" else "") {
        val c = printClauseUnicode(ClauseKind.Disjunction, it)
        if (multiline) c
        else "($c)"
    }
}