import syntaxtree.*;
import visitor.*;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;

public class TypeCheckingVisitor extends GJDepthFirst<String, String> {

    final Map<String, String> classList;
    final Map<String, Map<String, String>> classFields; // classname-> field_id,type
    final Map<String, Map<String, List<String>>> classMethods; // classname -> (fnName -> returntype, args...)
    final Map<String, Map<String, String>> fnVariables; // classname+fname -> id,type

    TypeCheckingVisitor(InitVisitor iv) {
        classList = iv.classList;
        classFields = iv.classFields;
        classMethods = iv.classMethods;
        fnVariables = iv.fnVariables;

    }

    boolean isSubclassOf(String class1, String class2) {

        while (classList.get(class1) != null) {
            class1 = classList.get(class1);
            if (class1.equals(class2)) {
                return true;
            }
        }
        return false;
    }

    // find the first superclass that has a specific function. if theres no such
    // superclass return null
    String superClassHasMethod(String classname, String fn) {
        while (classList.get(classname) != null) {
            classname = classList.get(classname);
            if (classMethods.containsKey(classname) && classMethods.get(classname).containsKey(fn)) {
                return classname;
            }
        }
        return null;

    }

    String superClassHasFields(String classname, String field) {
        while (classList.get(classname) != null) {
            classname = classList.get(classname);
            if (classFields.containsKey(classname) && classFields.get(classname).containsKey(field)) {
                return classname;
            }
        }
        return null;
    }

    @Override
    public String visit(MainClass n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        for (Node node : n.f14.nodes) {
            node.accept(this, null);
        }
        for (Node node : n.f15.nodes) {
            node.accept(this, classname + "+" + "main");
        }
        return null;
    }

    @Override
    public String visit(Type n, String argu) throws Exception {
        String type = n.f0.accept(this, null);
        if (type.equals("int") == false && type.equals("int[]") == false && type.equals("boolean") == false
                && type.equals("boolean[]") == false && classList.containsKey(type) == false) {
            throw new Exception("Type doesn't exist");
        }
        return type;
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    @Override
    public String visit(Block n, String argu) throws Exception {
        for (Node node : n.f1.nodes) {
            node.accept(this, argu);
        }
        // block can't be assigned etc, ignore its type
        return null;
    }

    @Override
    public String visit(NotExpression n, String argu) throws Exception {
        String expr_t = n.f1.accept(this, argu);
        if (expr_t.equals("boolean") == false) {
            throw new Exception("Not expression only accepts boolean expressions");
        }
        return "boolean";
    }

    @Override
    public String visit(Clause n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    @Override
    public String visit(IfStatement n, String argu) throws Exception {
        String expr = n.f2.accept(this, argu);
        if (expr.equals("boolean") == false) {
            throw new Exception("If statement only accepts boolean parameters");
        }

        n.f4.accept(this, argu);
        n.f6.accept(this, argu);
        // if statement doesn't have a type
        return null;

    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String argu) throws Exception {
        String expr = n.f2.accept(this, argu);
        if (expr.equals("boolean") == false) {
            throw new Exception("While statement only accepts boolean parameters");
        }
        n.f4.accept(this, argu);
        // while statement doesnt have a type
        return null;
    }

    /**
     * f0 -> IntegerLiteral()
     * | TrueLiteral()
     * | FalseLiteral()
     * | Identifier() 0------0
     * | ThisExpression()
     * | ArrayAllocationExpression()
     * | AllocationExpression()
     * | BracketExpression()
     */

    @Override
    public String visit(PrimaryExpression n, String argu) throws Exception {
        String[] class_fn = argu.split("\\+");
        String classname = class_fn[0];
        String fnname = class_fn[1];
        String ret_type = n.f0.accept(this, argu);

        return ret_type;

    }

    @Override
    public String visit(BooleanArrayAllocationExpression n, String argu) throws Exception {
        String size = n.f3.accept(this, argu);
        if (size.equals("int") == false) {
            throw new Exception("Array size not an int");
        }
        return "boolean[]";
    }

    @Override
    public String visit(IntegerArrayAllocationExpression n, String argu) throws Exception {
        String size = n.f3.accept(this, argu);
        if (size.equals("int") == false) {
            throw new Exception("Array size not an int");
        }
        return "int[]";
    }

    @Override
    public String visit(BracketExpression n, String argu) throws Exception {
        // type of the bracket expression = same as the expression inside of it
        return n.f1.accept(this, argu);
    }

    @Override
    public String visit(AllocationExpression n, String argu) throws Exception {
        String idtype = n.f1.accept(this, null);
        if (classList.containsKey(idtype) == false) {
            throw new Exception("Allocation expression: bad type");
        }
        return idtype;
    }

    @Override
    public String visit(MethodDeclaration n, String argu) throws Exception {

        String classname = argu;

        String ret_type = n.f1.accept(this, null);

        String fnName = n.f2.accept(this, null);

        String fnArgs = n.f4.present() ? n.f4.accept(this, null) : "";

        // check if a superclass has a method defined but with different formal
        // parameters
        String superclass = null;
        if ((superclass = superClassHasMethod(classname, fnName)) != null) {
            List<String> acceptedParamList_Super = classMethods.get(superclass).get(fnName);
            List<String> acceptedParamList = classMethods.get(classname).get(fnName);
            if (acceptedParamList.size() != acceptedParamList_Super.size()) {
                throw new Exception("Mismatched function types: " + superclass + "," + classname);
            }

            for (int i = 0; i < acceptedParamList.size(); i++) {
                if (acceptedParamList.get(i).equals(acceptedParamList_Super.get(i)) == false) {
                    throw new Exception("Mismatched argument types: " + superclass + ","
                            + classname);
                }
            }

        }

        for (Node node : n.f7.nodes) {
            node.accept(this, null);
        }

        for (Node node : n.f8.nodes) {
            node.accept(this, classname + "+" + fnName); // "classname+fnname"
        }

        String retexpr = n.f10.accept(this, classname + "+" + fnName);

        if (retexpr.equals(ret_type) == false && isSubclassOf(retexpr, ret_type) == false) {
            throw new Exception(
                    "The return expression of " + classname + ":" + fnName + " is incompatible with the return type");
        }
        // doesn't get used - could also be null
        return "METHOD";
    }

    @Override
    public String visit(Statement n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    @Override
    public String visit(AssignmentStatement n, String argu) throws Exception {

        String id_type = n.f0.accept(this, argu);
        String[] class_fn = argu.split("\\+");
        String classname = class_fn[0];
        String fn_name = class_fn[1];

        String expr_type = n.f2.accept(this, classname + "+" + fn_name);

        if (expr_type.equals(id_type) == false && isSubclassOf(expr_type, id_type) == false) {
            throw new Exception("incompatible assignment types: " + expr_type + "," + id_type);
        }
        // doesn't get used
        return "STATEMENT";
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    @Override
    public String visit(PrintStatement n, String argu) throws Exception {
        String printInput_type = n.f2.accept(this, argu);
        if (!printInput_type.equals("int")) {
            throw new Exception("Attempting to print non-Integer type");
        }
        // doesn't get used
        return "PRINT";
    }

    @Override
    public String visit(ClassDeclaration n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        for (Node node : n.f4.nodes) {
            node.accept(this, classname);
        }
        for (Node node : n.f3.nodes) {
            node.accept(this, classname);
        }
        // doesn't get used
        return "CLASS";
    }

    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String extendingClass = n.f1.accept(this, null);
        String superClass = n.f3.accept(this, null);

        for (Node node : n.f5.nodes) {
            node.accept(this, null);
        }

        for (Node node : n.f6.nodes) {
            node.accept(this, extendingClass);
        }
        // doesn't get used
        return "CLASSEXTENDS";
    }

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */

    @Override
    public String visit(AndExpression n, String argu) throws Exception {
        String operand1 = n.f0.accept(this, argu);
        String operand2 = n.f2.accept(this, argu);
        if (operand1.equals("boolean") == false || operand2.equals("boolean") == false) {
            throw new Exception(argu + ": '&&' operands are not of type 'boolean'");
        }

        return "boolean";
    }

    @Override
    public String visit(CompareExpression n, String argu) throws Exception {
        String operand1 = n.f0.accept(this, argu);
        String operand2 = n.f2.accept(this, argu);
        if (operand1.equals("int") == false || operand2.equals("int") == false) {
            throw new Exception(argu + ": '<' operands are not of type 'int'");
        }
        return "boolean";
    }

    @Override
    public String visit(PlusExpression n, String argu) throws Exception {
        String operand1 = n.f0.accept(this, argu);
        String operand2 = n.f2.accept(this, argu);
        if (operand1.equals("int") == false || operand2.equals("int") == false) {
            throw new Exception(argu + ": '+' operands are not of type 'int'");
        }
        return "int";
    }

    @Override
    public String visit(MinusExpression n, String argu) throws Exception {
        String operand1 = n.f0.accept(this, argu);
        String operand2 = n.f2.accept(this, argu);
        if (operand1.equals("int") == false || operand2.equals("int") == false) {
            throw new Exception(argu + ": '-' operands are not of type 'int'");
        }
        return "int";

    }

    @Override
    public String visit(TimesExpression n, String argu) throws Exception {
        String operand1 = n.f0.accept(this, argu);
        String operand2 = n.f2.accept(this, argu);
        if (operand1.equals("int") == false || operand2.equals("int") == false) {
            throw new Exception(argu + ": '*' operands are not of type 'int'");
        }
        return "int";
    }

    @Override
    public String visit(ArrayLookup n, String argu) throws Exception {

        String idtype = n.f0.accept(this, argu);

        String indextype = n.f2.accept(this, argu);
        if (idtype.equals("int[]") == false && idtype.equals("boolean[]") == false) {
            throw new Exception(argu + ": Not a valid array type");
        }
        if (indextype.equals("int") == false) {
            throw new Exception(argu + ": Array index is not of type 'int'");
        }
        if (idtype.equals("int[]")) {
            return "int";
        } else
            return "boolean";
    }

    @Override
    public String visit(ArrayLength n, String argu) throws Exception {
        String expr_type = n.f0.accept(this, argu);
        if (expr_type.equals("int[]") == false && expr_type.equals("boolean[]") == false) {
            throw new Exception(argu + ": not a valid input for array length");
        }
        return "int";
    }

    @Override
    public String visit(ArrayAssignmentStatement n, String argu) throws Exception {
        String id_type = n.f0.accept(this, argu);
        if (id_type.equals("int[]") == false && id_type.equals("boolean[]") == false) {
            throw new Exception("Left handside isn't of Array type");
        }
        String unit = null;
        if (id_type.equals("int[]"))
            unit = "int";
        else
            unit = "boolean";

        String expr_t = n.f2.accept(this, argu);
        if (expr_t.equals("int") == false) {
            throw new Exception("Array assignment index expression not an int");
        }

        String rhs_expr = n.f5.accept(this, argu);
        if (rhs_expr.equals(unit) == false) {
            throw new Exception("Right handside of array assignment of operation has incorrect type");
        }
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    @Override
    public String visit(MessageSend n, String argu) throws Exception {
        String[] class_fn = argu.split("\\+");
        String classPassed = class_fn[0];
        String funPassed = class_fn[1];

        String actualClass = null; // this -> classPassed, other ->expr_type

        String expr_type = n.f0.accept(this, argu);
        if (classList.containsKey(expr_type) == false) {
            throw new Exception(argu + ": Not a valid MessageSend type");
        }
        String fn_name = n.f2.accept(this, null);

        // check if the function in which the messagesend happens has the function --
        // else check the superclasses
        if (expr_type.equals(classPassed)) {
            actualClass = classPassed;
            if (classMethods.containsKey(classPassed) == false ||
                    classMethods.get(classPassed).containsKey(fn_name) == false) {
                String superClass = superClassHasMethod(classPassed, fn_name);
                if (superClass == null) {
                    throw new Exception("Class " + classPassed + "doesn't have method " + fn_name);
                }
                actualClass = superClass;
            }

        } else {
            // find the calling object's type and check if that class or its superclasses
            // have the method
            if (classList.containsKey(expr_type) == false) {
                throw new Exception("No such class type found");

            }
            actualClass = expr_type;
            if (classMethods.containsKey(expr_type) == false
                    || classMethods.get(expr_type).containsKey(fn_name) == false) {
                String superClass = superClassHasMethod(expr_type, fn_name);
                if (superClass == null) {
                    throw new Exception(expr_type + " doesn't have method: " + fn_name);
                }
                actualClass = superClass;
            }
        }

        String method_type = classMethods.get(actualClass).get(fn_name).get(0);

        List<String> acceptedParamList = classMethods.get(actualClass).get(fn_name);
        List<String> finalList = new ArrayList<String>();
        for (int i = 1; i < acceptedParamList.size(); i++) {
            finalList.add(acceptedParamList.get(i));
        }

        // check if the arguments match the formal parameters of the method
        String methodArgs = n.f4.present() ? n.f4.accept(this, argu) : "";

        String[] argsToArray = null;
        if (methodArgs.length() != 0) {
            argsToArray = methodArgs.split(",");
        } else {
            // hacky way to compare the formal parameter list with the argument list in the
            // case no arguments are passed
            // (splitting the empty string has array.length = 1 and formalparameter list
            // would have size = 0)
            argsToArray = new String[0];
        }

        if (argsToArray.length != finalList.size()) {
            throw new Exception("Missing or extra arguments in function call");
        }

        for (int i = 0; i < argsToArray.length; i++) {

            if (argsToArray[i].equals(finalList.get(i)) == false &&
                    isSubclassOf(argsToArray[i], finalList.get(i)) == false) {
                throw new Exception("Incompatible parameters for method call " + fn_name);
            }
        }

        return method_type;

    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    @Override
    public String visit(ExpressionList n, String argu) throws Exception {

        String ret = n.f0.accept(this, argu);

        if (n.f1 != null) {
            ret += n.f1.accept(this, argu);
        }

        return ret;
    }

    /**
     * f0 -> ( ExpressionTerm() )*
     */
    @Override
    public String visit(ExpressionTail n, String argu) throws Exception {
        String ret = "";
        for (Node node : n.f0.nodes) {
            ret += "," + node.accept(this, argu);
        }

        return ret;
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    @Override
    public String visit(ExpressionTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> AndExpression()
     * | CompareExpression()
     * | PlusExpression()
     * | MinusExpression()
     * | TimesExpression()
     * | ArrayLookup()
     * | ArrayLength()
     * | MessageSend()
     * | Clause()
     */

    @Override
    public String visit(Expression n, String argu) throws Exception {
        // if != int literal && !=bool literal (meaning its id), get actual type

        String[] class_fn = argu.split("\\+");
        String classname = class_fn[0];
        String fn_name = class_fn[1];

        String ret_type = n.f0.accept(this, argu);

        return ret_type;
    }

    @Override
    public String visit(VarDeclaration n, String argu) throws Exception {
        String type = n.f0.accept(this, null);
        // ignore -- can also be null
        return "VARDEC";
    }

    @Override
    public String visit(Identifier n, String argu) throws Exception {
        String id = n.f0.toString();

        if (argu != null) {
            String[] class_fn = argu.split("\\+");
            String classname = class_fn[0];
            String fn = class_fn[1];
            // return the right type, check function for local variables first, then class
            // fields, then superclasses
            if (fnVariables.containsKey(classname + "+" + fn)
                    && fnVariables.get(classname + "+" + fn).containsKey(id)) {
                return fnVariables.get(classname + "+" + fn).get(id);

            } else if (classFields.containsKey(classname) && classFields.get(classname).containsKey(id)) {
                return classFields.get(classname).get(id);
            } else if (superClassHasFields(classname, id) != null) {
                String superclass = superClassHasFields(classname, id);
                return classFields.get(superclass).get(id);
            } else {
                throw new Exception("ID " + id + " not found");
            }

        }
        return id;
    }

    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        String ret = n.f0.accept(this, null);

        if (n.f1 != null) {
            n.f1.accept(this, null);
        }

        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTail n, String argu) throws Exception {
        for (Node node : n.f0.nodes) {
            node.accept(this, null);
        }
        // ignore
        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, String argu) throws Exception {
        // error if doesn't exist
        String type = n.f0.accept(this, null);
        // ignore
        return null;
    }

    public String visit(IntegerLiteral n, String argu) {
        return "int";
    }

    public String visit(TrueLiteral n, String argu) {
        return "boolean";
    }

    public String visit(FalseLiteral n, String argu) {
        return "boolean";
    }

    public String visit(ThisExpression n, String argu) {
        return argu.split("\\+")[0];
    }

    @Override
    public String visit(BooleanArrayType n, String argu) {
        return "boolean[]";
    }

    @Override
    public String visit(IntegerArrayType n, String argu) {
        return "int[]";
    }

    public String visit(BooleanType n, String argu) {
        return "boolean";
    }

    public String visit(IntegerType n, String argu) {
        return "int";
    }
}