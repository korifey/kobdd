# KOBDD

##
Pure Kotlin high performance implementation of [Binary Decision Diagrams](https://en.wikipedia.org/wiki/Binary_decision_diagram). 
Can be used as: 
* Kotlin/Java library via [API](https://github.com/korifey/kobdd/blob/main/src/main/kotlin/org/kobdd/Kobdd.kt)

* Command Line Interface as [SAT solver](https://en.wikipedia.org/wiki/Boolean_satisfiability_problem) that takes standard input and produces standard output as specified [SAT competitions format](http://www.satcompetition.org/2004/format-solvers2004.html)

```shell
# java -jar kobdd-0.1.jar
v KOBDD SAT SOLVER, v0.1
v Solves formulas in CNF form. Input must as formatted according simplified DIMACS specified in http://www.satcompetition.org/2004/format-solvers2004.html


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

### Pigeonhole principle (PHP)
One of the goals for SAT-solver based on BDD is to show that it can solve [PHP](https://en.wikipedia.org/wiki/Pigeonhole_principle) problem
unsolvable by [CDCL](https://en.wikipedia.org/wiki/Conflict-driven_clause_learning) based SAT solver. 
Any CDCL solver works almost infinitely long for *PHP(31,30)* because it has to generate exponential
size proof to validate that solution is UNSAT ([The intractability of resolution, Armin Haken, Theoretical Computer Science
Volume 39, 1985, Pages 297-308](https://www.sciencedirect.com/science/article/pii/0304397585901446)).

For BDD-based solver it was shown that polynomial proof exists in 
[A. Atserias, P.G. Kolaitis, M.Y. Vardi, Constraint propagation as a proof system, in: CP, 2004, pp. 77–91](https://link.springer.com/chapter/10.1007/978-3-540-30201-8_9).
Later direct algorithm that with proven polynomial complexity was proposed in
[A direct construction of polynomial-size OBDD proof of pigeon hole problem, Wěi Chén, Wenhui Zhang, Information Processing Letters
Volume 109, Issue 10, 30 April 2009, Pages 472-477](https://www.sciencedirect.com/science/article/pii/S0020019009000143)

In current repository algorithm is implemented using KOBDD solver and can be verified by running tests in 
[TestPhp](https://github.com/korifey/kobdd/blob/main/src/test/kotlin/org/kobdd/TestPhp.kt)
class. Following experimental results for *PHP(n+1,n)* were obtained:

Problem | #clauses | #nodes | time
--- | --- | --- | ---
PHP(2, 1) | 3 | 5 | 0.000 sec
PHP(3, 2) | 9 | 40 | 0.001 sec
PHP(4, 3) | 22 | 155 | 0.002 sec
PHP(5, 4) | 45 | 425 | 0.001 sec
PHP(6, 5) | 81 | 956 | 0.008 sec
PHP(7, 6) | 133 | 1,890 | 0.003 sec
PHP(8, 7) | 204 | 3,410 | 0.007 sec
PHP(9, 8) | 297 | 5,745 | 0.006 sec
PHP(10, 9) | 415 | 9,175 | 0.006 sec
PHP(11, 10) | 561 | 14,036 | 0.006 sec
PHP(12, 11) | 738 | 20,725 | 0.004 sec
PHP(13, 12) | 949 | 29,705 | 0.009 sec
PHP(14, 13) | 1,197 | 41,510 | 0.011 sec
PHP(15, 14) | 1,485 | 56,750 | 0.019 sec
PHP(16, 15) | 1,816 | 76,116 | 0.018 sec
PHP(17, 16) | 2,193 | 100,385 | 0.028 sec
PHP(18, 17) | 2,619 | 130,425 | 0.020 sec
PHP(19, 18) | 3,097 | 167,200 | 0.030 sec
PHP(20, 19) | 3,630 | 211,775 | 0.034 sec
PHP(21, 20) | 4,221 | 265,321 | 0.042 sec
PHP(22, 21) | 4,873 | 329,120 | 0.060 sec
PHP(23, 22) | 5,589 | 404,570 | 0.077 sec
PHP(24, 23) | 6,372 | 493,190 | 0.106 sec
PHP(25, 24) | 7,225 | 596,625 | 0.104 sec
PHP(26, 25) | 8,151 | 716,651 | 0.144 sec
PHP(27, 26) | 9,153 | 855,180 | 0.221 sec
PHP(28, 27) | 10,234 | 1,014,265 | 0.210 sec
PHP(29, 28) | 11,397 | 1,196,105 | 0.384 sec
PHP(30, 29) | 12,645 | 1,403,050 | 0.236 sec
PHP(31, 30) | 13,981 | 1,637,606 | 0.329 sec
PHP(32, 31) | 15,408 | 1,902,440 | 0.377 sec
PHP(33, 32) | 16,929 | 2,200,385 | 0.601 sec
PHP(34, 33) | 18,547 | 2,534,445 | 0.495 sec
PHP(35, 34) | 20,265 | 2,907,800 | 0.613 sec
PHP(36, 35) | 22,086 | 3,323,811 | 0.712 sec
PHP(37, 36) | 24,013 | 3,786,025 | 0.790 sec
PHP(38, 37) | 26,049 | 4,298,180 | 1.043 sec
PHP(39, 38) | 28,197 | 4,864,210 | 0.836 sec
PHP(40, 39) | 30,460 | 5,488,250 | 0.894 sec
PHP(41, 40) | 32,841 | 6,174,641 | 1.019 sec
PHP(42, 41) | 35,343 | 6,927,935 | 1.209 sec
PHP(43, 42) | 37,969 | 7,752,900 | 1.471 sec
PHP(44, 43) | 40,722 | 8,654,525 | 1.811 sec
PHP(45, 44) | 43,605 | 9,638,025 | 1.544 sec
PHP(46, 45) | 46,621 | 10,708,846 | 1.701 sec
PHP(47, 46) | 49,773 | 11,872,670 | 1.926 sec
PHP(48, 47) | 53,064 | 13,135,420 | 2.093 sec
PHP(49, 48) | 56,497 | 14,503,265 | 2.399 sec
PHP(50, 49) | 60,075 | 15,982,625 | 2.932 sec
PHP(51, 50) | 63,801 | 17,580,176 | 4.002 sec
PHP(52, 51) | 67,678 | 19,302,855 | 3.226 sec
PHP(53, 52) | 71,709 | 21,157,865 | 3.582 sec
PHP(54, 53) | 75,897 | 23,152,680 | 3.775 sec
PHP(55, 54) | 80,245 | 25,295,050 | 4.522 sec
PHP(56, 55) | 84,756 | 27,593,006 | 5.126 sec
PHP(57, 56) | 89,433 | 30,054,865 | 5.631 sec
PHP(58, 57) | 94,279 | 32,689,235 | 6.111 sec
PHP(59, 58) | 99,297 | 35,505,020 | 7.785 sec
PHP(60, 59) | 104,490 | 38,511,425 | 6.312 sec
PHP(61, 60) | 109,861 | 41,717,961 | 7.529 sec
PHP(62, 61) | 115,413 | 45,134,450 | 7.697 sec
PHP(63, 62) | 121,149 | 48,771,030 | 8.330 sec
PHP(64, 63) | 127,072 | 52,638,160 | 9.122 sec
PHP(65, 64) | 133,185 | 56,746,625 | 10.515 sec
PHP(66, 65) | 139,491 | 61,107,541 | 11.091 sec
PHP(67, 66) | 145,993 | 65,732,360 | 11.792 sec
PHP(68, 67) | 152,694 | 70,632,875 | 16.474 sec
PHP(69, 68) | 159,597 | 75,821,225 | 13.214 sec
PHP(70, 69) | 166,705 | 81,309,900 | 13.114 sec
PHP(71, 70) | 174,021 | 87,111,746 | 13.898 sec
PHP(72, 71) | 181,548 | 93,239,970 | 14.679 sec
PHP(73, 72) | 189,289 | 99,708,145 | 16.379 sec
PHP(74, 73) | 197,247 | 106,530,215 | 18.243 sec
PHP(75, 74) | 205,425 | 113,720,500 | 19.681 sec