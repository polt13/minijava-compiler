**Compilation and running**

Use `make && java Main test.java`

`InitVisitor.java`: responsible for the first pass (fills data structures and does very basic checks)
`TypeCheckingVisitor.java`: responsible for the 2nd and final pass. Responsible for the majority of the semantics checking.


*** 

InitVisitor fills the different data structures with data types, method types. TypeChecking visitor evaluathes the correctness of the expressions and statements.

`classList`: a map that uses the names of the declared classes as keys and the name of its first superclass as the value (if it exists, else null).

`classFields`: map with the classname as key. Its value is  a map with class field identifiers as keys. The inner map's value is the type of this identifier.

`classMethods`: map with the classname as key. Its value is a map with the method names as keys. The inner map's value is a list of strings. The first element of the list is the return type of the method
and the rest (if they exist) are the types of the method's parameters, in the order that they are declared.

`fnVariables`: similar to classFields. The only difference is that the key is of the form `classname+function_name`. Also, the formal parameters of the method are added here (they count as local variables).

`offsetFields`, `offsetMethods`: class name  and field name that is mapped to an integer, which is the offset of the field/method respectively. It's of type LinkedHashMap so that the insertion order is maintained, since we need them to be
printed in the right order later.


`classSize`: The size of each class is calculated during  `calculateFieldsOffset`. For example:
```
class A {
	int x;
	boolean y;
	int foo() { return 1; }
}
```

has a classSize = 5, since we assume sizeof(x) = 4 and sizeof(y)= 1.

`calculateFieldOffset`:

The size of a class is used for thepurpose of calculating the offset of the fields of a subclass. If a class inherits from another one (ClassExtendsDeclaration), then it automatically has at minimum the same size as its parent class +
all the extra fields of its own (if they exist).

During class field declaration, the offset starts off at 0 and increases based on the type of class field that's being declared. If a class extends another then the offset is added to the size of the superclass.

e.g.
```

class A {
int x;
int y;
boolean z;
} // size 4+4+1 = 9

class B extends A {
int x;
} // offset(x) = sizeof(A) + 0 = 9

```

class_var_manager manages the variable declarations of the class and passes the offsets in the right order to calculateFieldOffset during the field's declaration.

`calculateMethodOffset`:

Similar to calculateFieldOffset. The main difference is that calculateMethodOffset returns a boolean to fn_prototype_manager, which is set to true if the offset that fn_prototype_manager passes must be increased by 8. The offset needs to
be increased only when a method of a subclass doesn't override the method of a superclass. If the subclass doesn't override the superclass then a new entry is created in the 'vtable' of the class and classTableSize grows. If it does indeed
override, then the classTableSize need not grow and we just need to find the offset of the method that's being overriden in one of the superclasses. In the same fashion as classSize, classTableSize matches the first superclass' classTableSize initially,
in the case where a superclass exists.

*** 

Every overriden visitor method returns a String (or null, if its result isn't needed elsewhere). Whenever an entity list needs to be handled (eg. list of variable declaration) a manager function is called (e.g. class_var_manager) that  receives
a Vector<Node>, which contains the Nodes that correspond to the variable declarations.

VarDeclaration returns a string of the form 'type:id'. After it's processed (splitting etc) the type and name of the class fields are extracted to be added to the map. Similar technique is used in MethodDeclaration. The first visitor also does some
very basic checks, such as if the name of the class or a field has been declared twice.

***

In TypeCheckingVisitor:

`isSubClassOf`: checks if class1 is subclass of class2
`superClassHasMethod`,`superClassHasFields`: checks if a superclass has a field or a method and if so, returns the name of that super class (the first one that matches in the hierarchy)

The former is used so that we can check that the  method prototype in a subclass matches the one in the superclass. It's also used in the case where only the superclass contains the method declaration (subclass doesn't override).
The latter is used in cases where the identifier is in a superclass and not inside of the class itself.

`Identifier` of TypeCheckingVisitor is called with null as argument when Identifier needs to be explicitly returned as is (e.g. AllocationExpression), or is called with `class+function_name` as argument when the type of the identifier
is needed (for example, when the ID is inside of an expression).

All of the methods that deal with expressions receive `classname+function_name` as argument (function and class in which they exist) to check for correctness - if it exists, if it's the right type etc

