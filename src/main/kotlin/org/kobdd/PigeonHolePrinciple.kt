package org.kobdd


/**
 * Encoding according
 * [A direct construction of polynomial-size OBDD proof of pigeon hole problem, Wei Chén a,b,∗, Wenhui Zhang a]
 */
fun generatePhpClauses(pigeons : Int, holes: Int) : Clauses {
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
