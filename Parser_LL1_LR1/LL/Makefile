.PHONY: all
all: calculator

calculator: Calculator.java Main.java ParseError.java
	javac $^

run: calculator
	java Main
clean: 
	rm -f *.class