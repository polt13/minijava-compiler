PART 1 
***

Grammar:

goal -> expr
expr -> term  expr2
expr2 -> ^ term expr2 | ε
term -> numexpr term2
term2 -> & numexpr term2 | ε
numexpr -> number | ( expr )

FIRST+(expr) = { num, '(' } , FOLLOW(expr) = { ')', $ }
FIRST+(expr2) = { '^', ')', $ } , FOLLOW(expr2) = { $ }
FIRST+(term) = { num, '(' }, FOLLOW(term) = { '^',  ')', $ }
FIRST+(term2) = { '&', '^', ')', $ }, FOLLOW(term2) = { '^', ')', $ }
FIRST+(numexpr) = { num, '(' }, FOLLOW(numexpr) = { '&', '^', ')', $)

Where num = any single digit number, $ = EOF

PART 2
***

FUNDIFFER is used in order to be able to distinguish between a function call and a function definition (avoid shift/reduce). If a ')' is followed by '{' it's explicitly recognised as a single token. 

To allow for arbitrary amount of whitespace between `)` and `{` ENDLESSWS is used. Additionally, id matches identifiers for functions and function parameters. Finally, each token (e.g. "reverse") is matched as a single token.


In parser.cup,  the starting rule (start) consists of all the possible variations of programs that can be given : a list of function declarations and expressions (global scope), just function declarations, 
just expressions, none of the above (empty file).

There are 2 different kind of expressions:

* expr_top: top level functions, meaning not inside of a function's body. They don't accept ID as an operand.
* expr_fn: only inside of a function's body. They accept ID as operand.

rev_fn,rev_top etc only accept expr_fn,expr_top expressions respectively.

This is needed - otherwise, reduce/reduce and shift/reduce problems arise in function call arguments and in function call definition parameters.
