import syntaxtree.*;
import visitor.*;

import java.io.*;

import java.util.*;

public class VTable_Visitor extends GJDepthFirst<String, String> {
    PrintWriter pw;
    Map<String, List<String>> methodsIR; // A -> ir version of function

    VTable_Visitor(PrintWriter pw) {
        this.pw = pw;
        methodsIR = new LinkedHashMap<>();
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
        String classname = argu;
        String fnName = n.f2.accept(this, null);
        String ret_type = n.f1.accept(this, "type");
        String fnArgs = n.f4.present() ? n.f4.accept(this, null) : "";

        String arglist = "";
        if (fnArgs.contains(",")) {
            String[] argsToArray = fnArgs.split(",");
            arglist = "i8*";
            for (int i = 0; i < argsToArray.length; i++) {
                arglist += "," + argsToArray[i];
            }
        } else {
            if (fnArgs.length() == 0)
                arglist = "i8*";
            else
                arglist = "i8*," + fnArgs;

        }

        String method_prot = "i8* bitcast (" + ret_type + " (" + arglist + ")* " + "@" + classname + "." + fnName
                + " to i8*)";

        if (methodsIR.containsKey(classname) == false) {
            ArrayList<String> methodList = new ArrayList<>();
            methodList.add(method_prot);
            methodsIR.put(classname, methodList);
        } else {
            methodsIR.get(classname).add(method_prot);
        }
        return fnName;
    }

    public String visit(MainClass n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        ArrayList<String> methodList = new ArrayList<>();
        methodsIR.put(classname, methodList);
        pw.write(String.format("@.%s_vtable = global [0 x i8*] []\n", classname));
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
        String className = n.f1.accept(this, null);

        String vtable = "";

        // access method from nodes vector
        for (int i = 0; i < n.f4.nodes.size(); i++) {
            // add method signature to map
            n.f4.nodes.get(i).accept(this, className);
            // i-th index in arraylist is the method prototype itself
            String method_prot = methodsIR.get(className).get(i);
            vtable += method_prot;
            if (i + 1 < n.f4.nodes.size()) {
                vtable += ",\n";
            }
        }

        // if has functions
        if (methodsIR.containsKey(className)) {
            vtable = String.format("@.%s_vtable = global [%d x i8*] [", className, methodsIR.get(className).size())
                    + vtable
                    + "]\n";
        } else {
            vtable = String.format("@.%s_vtable = global [%d x i8*] [", className, 0)
                    + vtable
                    + "]\n";
        }

        // write class vtable to file
        pw.write(vtable);
        return null;
    }

    @Override
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {

        String className = n.f1.accept(this, null);

        String superclass = n.f3.accept(this, null);

        String vtable = "";

        // make subclass vtable same as superclass initially
        // copy the value of the list so that the modificatiosn dont affect superclass
        // version
        methodsIR.put(className, new ArrayList<>(methodsIR.get(superclass)));

        List<String> classMethods = methodsIR.get(className);

        // access method from nodes vector
        for (int i = 0; i < n.f6.nodes.size(); i++) {
            // add method signature to map
            String method_name = n.f6.nodes.get(i).accept(this, className);
            // check if subclass is overriding superclass' method
            String superclass_version = classMethods.get(classMethods.size() - 1).replaceFirst(
                    className + "\\." + method_name,
                    superclass + "\\." + method_name);

            int subclassOverrides = methodsIR.get(className).indexOf(superclass_version);

            if (subclassOverrides != -1) {
                // if subclass overrides, replace the existing superclass' version
                classMethods.set(subclassOverrides, classMethods.get(classMethods.size() - 1));
                classMethods.remove(classMethods.size() - 1);
            }

        }

        for (int i = 0; i < classMethods.size(); ++i) {
            vtable += methodsIR.get(className).get(i);
            if (i + 1 < methodsIR.get(className).size()) {
                vtable += ",\n";
            }
        }

        if (methodsIR.containsKey(className)) {
            vtable = String.format("@.%s_vtable = global [%d x i8*] [", className, methodsIR.get(className).size())
                    + vtable
                    + "]\n";
        } else {
            vtable = String.format("@.%s_vtable = global [%d x i8*] [", className, 0)
                    + vtable
                    + "]\n";
        }
        // write class vtable to file
        pw.write(vtable);
        return null;
    }

    @Override
    public String visit(BooleanArrayType n, String argu) {
        return "%_boolarr*";
    }

    @Override
    public String visit(IntegerArrayType n, String argu) {
        return "%_intarr*";
    }

    public String visit(BooleanType n, String argu) {
        return "i1";
    }

    public String visit(IntegerType n, String argu) {
        return "i32";
    }

    @Override
    public String visit(Identifier n, String argu) {
        if (argu != null) {
            // return i8* instead of class name in cases where it's needed
            return "i8*";
        }
        return n.f0.toString();
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
    @Override
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

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
        String type = n.f0.accept(this, "type");
        return type;
    }

}