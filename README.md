# kobdd
Pure Kotlin high performance implementation of [Binary Decision Diagrams](https://en.wikipedia.org/wiki/Binary_decision_diagram).
Distributed as [SAT solver](https://en.wikipedia.org/wiki/Boolean_satisfiability_problem) that takes standart input and produces standard output as specified [SAT competitions format](http://www.satcompetition.org/2004/format-solvers2004.html)


```shell
# java -jar kobdd-0.1.jar
v KOBDD SAT SOLVER, v0.1
v Solves formulas in CNF form as specified in http://www.satcompetition.org/2004/format-solvers2004.html


c REQUEST
c 
c comments
c 
p cnf 5 3
1 -5 4 0
-1 5 3 4 0
-3 -4 0


v Start processing CNF request with 5 variables and 3 clauses
v processed clause [1, -5, 4]
v processed clause [-1, 5, 3, 4]
v processed clause [-3, -4]
v Request processed in 0.039 sec
s SATISFIABLE
v 1 3 -4
c Done
```


