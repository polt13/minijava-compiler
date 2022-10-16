all: compile

compile:
	java -jar ../jtb132di.jar -te minijava.jj
	java -jar ../javacc5.jar minijava-jtb.jj
	javac InitVisitor.java 
	javac Semantics_Analysis.java
	javac VTable_Visitor.java
	javac Generator_Visitor.java
	javac TypeCheckingVisitor.java
	javac Main.java

clean:
	rm -f *.class *~

