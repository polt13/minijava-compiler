**Compilation and Running** 

- `make && java Main Test.java`
- `clang Test.ll -o Test && ./Test`

VTableVisitor is constructed using a print writer as argument (which is linked to the .ll file to which the produced IR code will be written). It also creates a new HashMap in which the method signatures are inserted for each class.
VTable_Visitor is only interested in MainClass,ClassDeclaration,ClassExtendsDeclaration (without the VarDeclaration of the classes) and MethodDeclarations. For each method it collects
the return type, its arguments (IR form) and stores them in alist. In each ClassExtendsDeclaration, the vtable of the superclass is copied 1:1 in the child's methodsIR and then, for each function declaration of the child, we search for whether
or not the function that's being declared overrides some superclass' function, so that it can be inserted in the proper index in the list that holds the methods, in the order that they are in the vtable.

*** 

`helpers.help` also includes information that need to be inserted in every file that produces LLVM/Clang IR code, such as the declarations for printf. Along with these, %_intarr and %_boolarr are included in the beginning of the file (through Main.java).

Generator_Visitor writes to the printwriter that's passed to its constructor. It also accepts an InitVisitor that contains various maps with useful information for Generator_Visitor. Also, it keeps track of certain counters
(RegisterCounter, gets reset everytime a MethodDeclaration is finished being processed) and an IfCounter,LoopCounter,OOBCounter which are only ever increased.

Through string manipulations, GEnerator_Visitor in MethodDeclaration processes the name of the register that's in the parameters (e.g. %.x), extracts `x` and copies its value to a new register of the form `%x` (basically a stack copy). 
The vtable of a class is also parsed during MessageSend to extract the return type, parameter list etc

Type_Converter receives a Java type and converts it to the equivalent IR one.

Some visit methods receive the classname or a combination of the class name and function name as parameter (same as the semantics analysis visitor). In certain cases, such as the Identifier, they also operate on an extra 'attribute'. For instance,
Identifier can receive an attribute like :GETARR:, :GETADDRESS:, :GETCLASS:. 

- The first attribute indicates that the Identifier needs to include the array type in its returned object (%_intarr*,%_boolarr*). This attribute is especially
useful in ArrayLookup, ArrayLength so that we can know the arraytype on which the operation is performed (for example, in the case of something like (new int[30]).length).
- The second attribute indicates that the Identifier should return a register with just the address of the object to which the Identifier corresponds (e.g. %x if x is a local variable, or a new register to which the address of the class
object is passed). This is useful in AssignmentStatements so that we can `store` the value of the `rightmost` expression directly to this address.
- The third attribute indicates that the name of the class (e.g. A) should be included, which is useful in MessageSend.

At most one of these attributes is present ass suffix in the 2nd argument of visit. Additionally, in some cases the attribute needs to be removed from the parameter before the next time it' accepted. For instance, IntegerArrayAllocationExpression
removes ":GETARR:" from `argu` if it exists (e.g. when (new int[30])[0] occurs) before it `accept`s the Expression that includes the size. In this specific case, this is done since only `IntegerArrayAllocationExpression` 
itself cares about the attribute (and returns the array type, in this case %_intarr*).

In AllocationExpressions for arrays, calloc is called using as many bytes are requested  + 4 (4 is the size in bytes of the first field of the struct, i32, which holds the array size info). Also, in cases where offsets
are calculated, +8 is added, since we know that the first 8 bytes correspond to the vtable pointer. Finally, for each method offset we divide by 8, considering that the vtable is an i8* array (that's how we get the pointer to the next
method).

AllocationExpression for other objecs use the previously calculated classSize (the visitors from the 2nd part of the project already include this), after it's increased by 8 (to account for the vtable pointer).

The Vtable pointer is stored inside of the object (in the first position) after `new`, inside of the AllocationExpression. Therefore, if an object isn't initialised, a MessageSend on it can lead to Segmentation Fault.


In regards to the Identifier's visit: this function is responsible for finding the register of the identifier and whether or not it's a class field, a function's local variable or a superclass' field. Lastly, MessageSend checks 
if the object on which the method is called contains the object or if it's part of its superclass.


