import syntaxtree.*;
import visitor.*;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;

public class InitVisitor extends GJDepthFirst<String, String> {

    Map<String, String> classList; // key = classname, value= nameofsuperclass or null (if it doesnt exist)
    Map<String, Map<String, String>> classFields; // classname-> field_id,type
    Map<String, Map<String, List<String>>> classMethods; // classname -> (fnName -> returntype, args...)
    Map<String, Map<String, String>> fnVariables; // classname+fname -> id,type
    Map<String, Map<String, Integer>> offsetFields; // class -> field -> offset
    Map<String, Map<String, Integer>> offsetMethods; // class -> fn -> offset

    // calculate the offset of the identifiers and class methods based on the class
    // size and class (virtual) table size of superclass, if it exists
    Map<String, Integer> classSize;
    Map<String, Integer> classTableSize;
    String mainClass; // for printing purpsoes

    InitVisitor() {
        classList = new LinkedHashMap<>();
        classFields = new HashMap<>();
        classMethods = new HashMap<>();
        fnVariables = new HashMap<>();
        // maintain visit order for printing the offsets later
        offsetFields = new LinkedHashMap<>();
        offsetMethods = new LinkedHashMap<>();
        // size of class based on fields
        classSize = new HashMap<>();
        // size of class 'vtable'
        classTableSize = new HashMap<>();
    }

    void printOffsets() {
        for (String classname : classList.keySet()) {
            if (mainClass.equals(classname)) { // dont print main class
                continue;
            }
            System.out.println("-----------Class " + classname + "-----------");
            System.out.println("--Variables---");
            if (offsetFields.containsKey(classname)) {
                for (String id : offsetFields.get(classname).keySet()) {
                    System.out.println(classname + "." + id + " : " + offsetFields.get(classname).get(id));
                }
            }
            System.out.println("---Methods---");
            if (offsetMethods.containsKey(classname)) {
                for (String fn : offsetMethods.get(classname).keySet()) {
                    System.out.println(classname + "." + fn + " : " + offsetMethods.get(classname).get(fn));
                }
            }
        }

    }

    boolean calculateMethodOffset(String classname, String fn_name, int offset) {
        // if class has no superclass, simply increase the offset by 8 for each function
        // everytime
        if (classList.get(classname) == null) {

            if (offsetMethods.containsKey(classname) == false) {

                Map<String, Integer> tmp = new LinkedHashMap<>();
                tmp.put(fn_name, offset);
                offsetMethods.put(classname, tmp);

            } else {
                offsetMethods.get(classname).put(fn_name, offset);
            }

            classTableSize.put(classname, classTableSize.get(classname) + 8);

        } else {
            // take into account the superclass Methods, if they exist and plan the offset
            // accordingly
            String superclass = classList.get(classname);
            String t = classname;
            while (classList.get(t) != null) {
                t = classList.get(t);

                if (offsetMethods.containsKey(t) && offsetMethods.get(t).containsKey(fn_name)) {

                    offset = offsetMethods.get(t).get(fn_name);

                    if (offsetMethods.containsKey(classname) == false) {
                        Map<String, Integer> tmp = new HashMap<>();
                        tmp.put(fn_name, offset);
                        offsetMethods.put(classname, tmp);
                    } else {
                        offsetMethods.get(classname).put(fn_name, offset);
                    }

                    return false;
                }
            }

            // offsets based on superclass, if class derives but function not overriden
            if (offsetMethods.containsKey(classname) == false) {
                Map<String, Integer> tmp = new LinkedHashMap<>();
                tmp.put(fn_name, offset + classTableSize.get(superclass));
                offsetMethods.put(classname, tmp);

            } else {
                offsetMethods.get(classname).put(fn_name, offset + classTableSize.get(superclass));
            }

            // new function declared (not inherited) -- increase table size
            classTableSize.put(classname, classTableSize.get(classname) + 8);

        }
        // System.out.println(classname + "." + fn_name + " : " +
        // offsetMethods.get(classname).get(fn_name));
        return true;

    }

    void calculateFieldOffset(String classname, String id, String type, int offset) {
        // if class has no fields increase the offset everytime
        if (classList.get(classname) == null) {

            if (offsetFields.containsKey(classname) == false) {
                offsetFields.put(classname, new LinkedHashMap<String, Integer>() {
                    {
                        put(id, offset);
                    }
                });
            } else {
                offsetFields.get(classname).put(id, offset);
            }

        } else {
            String superclass = classList.get(classname);
            if (offsetFields.containsKey(classname) == false) {
                offsetFields.put(classname, new LinkedHashMap<String, Integer>() {
                    {
                        put(id, offset + classSize.get(superclass));
                    }
                });

            } else

            {

                offsetFields.get(classname).put(id, offset + classSize.get(superclass));
            }

        }
        if (type.equals("int")) {
            classSize.put(classname, classSize.get(classname) + 4);
        } else if (type.equals("boolean")) {
            classSize.put(classname, classSize.get(classname) + 1);
        } else {
            classSize.put(classname, classSize.get(classname) + 8);
        }
        // System.out.println(classname + "." + id + " : " +
        // offsetFields.get(classname).get(id));
    }

    void class_var_manager(String classname, Vector<Node> nodes) throws Exception {
        int offset = 0;
        for (Node n : nodes) {
            String var = n.accept(this, null);

            StringTokenizer vartok = new StringTokenizer(var, ":");
            String type = vartok.nextToken();
            String id = vartok.nextToken();
            calculateFieldOffset(classname, id, type, offset);

            if (type.equals("int")) {
                offset += 4;
            } else if (type.equals("boolean")) {
                offset += 1;
            } else {
                offset += 8;
            }

            if (!classFields.containsKey(classname)) {
                classFields.put(classname, new HashMap<String, String>() {
                    {
                        put(id, type);
                    }
                });
            } else {
                if (classFields.get(classname).containsKey(id)) { // check if Id already exists
                    throw new Exception("Redeclared class " + classname + " variable");
                }
                classFields.get(classname).put(id, type);
            }
        }

    }

    void fn_prototype_manager(String classname, Vector<Node> nodes) throws Exception {
        int offset = 0;
        for (Node n : nodes) {
            String fn = n.accept(this, classname);
            StringTokenizer fntok = new StringTokenizer(fn, ",");
            String return_type = fntok.nextToken();
            String fn_name = fntok.nextToken();
            boolean increaseOffset = calculateMethodOffset(classname, fn_name, offset);
            // if the function is overriding a superclass one, don't increase the offset
            if (increaseOffset == true)
                offset += 8;
            List<String> signature = new ArrayList<String>() {
                {
                    add(return_type); // index_0 = return type, index_1 = int x...
                }
            };
            while (fntok.hasMoreTokens()) {
                String[] argument_parts = fntok.nextToken().trim().split(" ");
                String argument_type = argument_parts[0];
                String argument_id = argument_parts[1];

                // treat function arguments as normal function variables
                if (!fnVariables.containsKey(classname + "+" + fn_name)) {
                    fnVariables.put(classname + "+" + fn_name, new HashMap<String, String>() {
                        {
                            put(argument_id, argument_type);
                        }
                    });
                } else {
                    if (fnVariables.get(classname + "+" + fn_name).containsKey(argument_id)) {
                        throw new Exception("Function variable in " + classname + ":" + fn_name + " redeclared");
                    }
                    fnVariables.get(classname + "+" + fn_name).put(argument_id, argument_type);
                }

                signature.add(argument_parts[0]);
            }

            if (!classMethods.containsKey(classname)) {
                classMethods.put(classname, new HashMap<String, List<String>>() {
                    {
                        put(fn_name, signature);
                    }
                });
            } else {
                if (classMethods.get(classname).containsKey(fn_name)) {
                    throw new Exception("Function in " + classname + " redeclared");
                }
                classMethods.get(classname).put(fn_name, signature);
            }

        }
    }

    void fn_var_manager(String classname, String fn_name, Vector<Node> nodes) throws Exception {
        for (Node n : nodes) {
            String var = n.accept(this, null);
            StringTokenizer vartok = new StringTokenizer(var, ":");
            String type = vartok.nextToken();
            String id = vartok.nextToken();

            if (!fnVariables.containsKey(classname + "+" + fn_name)) {
                fnVariables.put(classname + "+" + fn_name, new HashMap<String, String>() {
                    {
                        put(id, type);
                    }
                });
            } else {
                if (fnVariables.get(classname + "+" + fn_name).containsKey(id)) {
                    throw new Exception("Function variable in " + classname + ":" + fn_name + " redeclared");
                }
                fnVariables.get(classname + "+" + fn_name).put(id, type);
            }

        }
    }

    void main_var_manager(String classname, MainClass node) throws Exception {
        for (Node n : node.f14.nodes) {
            String var = n.accept(this, null);
            StringTokenizer vartok = new StringTokenizer(var, ":");
            String type = vartok.nextToken();
            String id = vartok.nextToken();

            if (!fnVariables.containsKey(classname + "+" + "main")) {
                fnVariables.put(classname + "+" + "main", new HashMap<String, String>() {
                    {
                        put(id, type);
                    }
                });
            } else {
                if (fnVariables.get(classname + "+" + "main").containsKey(id)) {
                    throw new Exception("Function variable in " + classname + ":" + "main" + " redeclared");
                }
                fnVariables.get(classname + "+" + "main").put(id, type);
            }
            //
        }
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    @Override
    public String visit(MainClass n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        mainClass = classname;
        String arguID = n.f11.accept(this, null);
        classList.put(classname, null);
        classSize.put(classname, 0);
        classTableSize.put(classname, 0);
        fnVariables.put(classname + "+" + "main", new HashMap<String, String>() {
            {
                put(arguID, "String[]");
            }
        });

        this.main_var_manager(classname, n);

        System.out.println();

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public String visit(ClassDeclaration n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        classSize.put(classname, 0);
        classTableSize.put(classname, 0);
        if (!classList.containsKey(classname))
            classList.put(classname, null);
        else {
            // check if class has already been declared
            throw new Exception("Duplicate class declaration");
        }

        this.class_var_manager(classname, n.f3.nodes);
        this.fn_prototype_manager(classname, n.f4.nodes);

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public String visit(VarDeclaration n, String argu) throws Exception {
        String ftype = n.f0.accept(this, null);
        String fid = n.f1.accept(this, null);
        return ftype + ":" + fid;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public String visit(MethodDeclaration n, String argu) throws Exception {

        String fnType = n.f1.accept(this, null);
        String fnName = n.f2.accept(this, null);
        String fnArgs = n.f4.present() ? n.f4.accept(this, null) : "";

        String[] argsToArray = fnArgs.split(",");

        String funProt = fnType + "," + fnName;
        for (int i = 0; i < argsToArray.length; i++) {
            funProt += ("," + argsToArray[i]);
        }

        this.fn_var_manager(argu, fnName, n.f7.nodes);

        return funProt;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String extendingClass = n.f1.accept(this, null);
        String superClass = n.f3.accept(this, null);
        if (!classList.containsKey(superClass)) {
            throw new Exception("Class " + superClass + " not declared");
        }
        // child class has at least the same vtable size and class size
        classSize.put(extendingClass, classSize.get(superClass));
        classTableSize.put(extendingClass, classTableSize.get(superClass));

        if (classList.containsKey(extendingClass)) {
            throw new Exception("Extending class " + extendingClass + " redeclared");
        }
        classList.put(extendingClass, superClass);

        this.class_var_manager(extendingClass, n.f5.nodes);
        this.fn_prototype_manager(extendingClass, n.f6.nodes);

        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        String ret = n.f0.accept(this, null);

        if (n.f1 != null) {
            ret += n.f1.accept(this, null);
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
        String ret = "";
        for (Node node : n.f0.nodes) {
            ret += "," + node.accept(this, null);
        }

        return ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, String argu) throws Exception {
        String type = n.f0.accept(this, null);
        String name = n.f1.accept(this, null);
        return type + " " + name;
    }

    /**
     * f0 -> BooleanArrayType()
     * | IntegerArrayType()
     */
    @Override
    public String visit(ArrayType n, String argu) throws Exception {
        return n.f0.accept(this, null);
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

    @Override
    public String visit(Identifier n, String argu) {
        return n.f0.toString();
    }
}