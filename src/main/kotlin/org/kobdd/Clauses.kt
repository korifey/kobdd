package org.kobdd

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