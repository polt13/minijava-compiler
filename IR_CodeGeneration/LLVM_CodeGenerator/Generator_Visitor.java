import syntaxtree.*;
import visitor.*;

import java.io.*;

import java.util.*;

// %_boolarr = type { i32, [0 x i1] }
// %_intarr = type { i32, [0 x i32] }

public class Generator_Visitor extends GJDepthFirst<String, String> {
    PrintWriter pw;
    InitVisitor iv;
    VTable_Visitor vt;
    Map<String, List<String>> methodsIR; // A -> ir version of function
    Integer RegisterCounter;
    Integer IfCounter;
    Integer LoopCounter;
    Integer OOBCounter;

    Generator_Visitor(PrintWriter pw, InitVisitor iv, VTable_Visitor vt) {
        this.pw = pw;
        this.iv = iv;
        this.vt = vt;

        // reset counter every time a method declaration is over.
        RegisterCounter = 0;
        IfCounter = 0;
        LoopCounter = 0;
        OOBCounter = 0;

    }

    String type_converter(String jtype) {
        if (jtype.equals("int")) {
            return "i32";
        } else if (jtype.equals("boolean")) {
            return "i1";
        } else if (jtype.equals("int[]")) {
            return "%_intarr*";
        } else if (jtype.equals("boolean[]")) {
            return "%_boolarr*";
        } else {
            return "i8*";
        }
    }

    String superClassHasFields(String classname, String field) {
        while (iv.classList.get(classname) != null) {
            classname = iv.classList.get(classname);
            if (iv.classFields.containsKey(classname) && iv.classFields.get(classname).containsKey(field)) {
                return classname;
            }
        }
        return null;
    }

    String superClassHasMethod(String classname, String fn) {
        while (iv.classList.get(classname) != null) {
            classname = iv.classList.get(classname);
            if (iv.classMethods.containsKey(classname) && iv.classMethods.get(classname).containsKey(fn)) {
                return classname;
            }
        }
        return null;

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
        String reg = n.f2.accept(this, argu);
        String printer = String.format("call void @print_int(i32 %s)\n", reg);
        pw.write("\t" + printer);
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

        // remove ":GETCLASS:" attribute for subsequent calls
        boolean concat_class = false;
        if (argu.endsWith(":GETCLASS:")) {
            argu = argu.split(":")[0];
            concat_class = true;
        }

        boolean concat_type = false;
        if (argu.endsWith(":GETARR:")) {
            argu = argu.split(":")[0];
            concat_type = true;
        }

        String[] class_fn = argu.split("\\+");
        String classname = class_fn[0];
        String fnName = class_fn[1];

        // obj is the name of the register holding the object
        String obj;
        // classtype is the name of the class that has the method
        String classtype;

        // if PrimaryExpression is AllocationExpress, need to get the type of the
        // allocated object
        String objreg = n.f0.accept(this, classname + "+" + fnName + ":GETCLASS:");

        if (objreg.equals("%this")) {

            obj = "%this";
            classtype = classname;

        } else {
            String[] obj_class = objreg.split(":");
            obj = obj_class[0];
            classtype = obj_class[1];
        }

        // in the case of A a = new B(); then a has classtype A but it doesn't matter
        // since the offset for the functions is the same

        String method_name = n.f2.accept(this, null);

        Integer methodOffset = 0;

        if (iv.classMethods.containsKey(classtype) == false
                || iv.classMethods.get(classtype).containsKey(method_name) == false) {
            String superc = superClassHasMethod(classtype, method_name);
            methodOffset = iv.offsetMethods.get(superc).get(method_name) / 8;
        } else {
            methodOffset = iv.offsetMethods.get(classtype).get(method_name) / 8;
        }

        String method_prot = vt.methodsIR.get(classtype).get(methodOffset);

        // parse the string stored in the vtable to only get the return type and the
        // prototype of the function call

        Integer funStartsAt = method_prot.indexOf("(") + 1;
        Integer funEndsAt = method_prot.lastIndexOf(" to");

        // extract only the return type and param list itself
        String method_type = method_prot.substring(funStartsAt, funEndsAt).split("@")[0];

        String ret = method_type.split(" ")[0];

        Integer argsStart = method_type.indexOf("(") + 1;
        Integer argsEnd = method_type.lastIndexOf(")");
        // for type info

        String[] args_array = method_type.substring(argsStart, argsEnd).split(",");

        String params = n.f4.present() ? n.f4.accept(this, argu) : "";

        String expr_list = "i8* " + obj;

        if (args_array.length > 1) {
            int i = 1;
            // prefix every register with the type of it
            for (String s : params.split(",")) {
                expr_list += "," + args_array[i] + " " + s;
                i++;
            }
        }

        ////////////////////////////////////////////////////////////////////////////////

        String t1 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String t2 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String t3 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String t4 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String t5 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String t6 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String bcast = String.format("%s = bitcast i8* %s to i8***\n", t1, obj);
        String l1 = String.format("%s = load i8**, i8*** %s\n", t2, t1);
        String gep = String.format("%s = getelementptr i8*, i8** %s, i32 %d\n", t3, t2, methodOffset);
        String l2 = String.format("%s = load i8*, i8** %s\n", t4, t3);
        String bcast2 = String.format("%s = bitcast i8* %s to %s\n", t5, t4, method_type);
        String call = String.format("%s = call %s %s(%s)\n", t6, ret, t5, expr_list);
        pw.write("\t" + bcast);
        pw.write("\t" + l1);
        pw.write("\t" + gep);
        pw.write("\t" + l2);
        pw.write("\t" + bcast2);
        pw.write("\t" + call);

        // get classname in case its called from allocation expression
        if (concat_class) {
            return String.format("%s:%s", t6, classtype);
        }

        // Get array type in case its called from array lookup
        if (concat_type) {
            return String.format("%s:%s", t6, ret);
        }

        return t6;
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

        // copy the arguments into diff registers

        String stack_copy = "";
        String arglist = "i8* %this";
        if (fnArgs.contains(",")) {
            String[] argsToArray = fnArgs.split(",");
            arglist = "i8* %this";
            for (int i = 0; i < argsToArray.length; i++) {

                // get type
                String type = argsToArray[i].split(" ")[0];
                // get varname without the "%."
                String var_name = argsToArray[i].substring(argsToArray[i].indexOf("%.") + 2);

                stack_copy += String.format("\t%%%s = alloca %s\n", var_name, type);
                stack_copy += String.format("\tstore  %s, %s* %%%s\n", argsToArray[i], type, var_name);

                arglist += "," + argsToArray[i];
            }
        } else {
            if (fnArgs.length() > 0) {
                arglist += "," + fnArgs;
                // get type
                String type = fnArgs.split(" ")[0];
                // get varname without the %.
                String var_name = fnArgs.substring(fnArgs.indexOf("%.") + 2);

                stack_copy += String.format("\t%%%s = alloca %s\n", var_name, type);
                stack_copy += String.format("\tstore %s, %s* %%%s\n", fnArgs, type, var_name);
            }

        }
        String fun = String.format("define %s @%s.%s(%s) {\n", ret_type, classname, fnName, arglist);
        pw.write(fun);
        pw.write(stack_copy);

        for (Node v : n.f7.nodes) {
            v.accept(this, classname + "+" + fnName);
        }

        for (Node s : n.f8.nodes) {
            s.accept(this, classname + "+" + fnName);
        }

        String ret_expr = n.f10.accept(this, classname + "+" + fnName);

        String ret = String.format("\tret %s %s\n", ret_type, ret_expr);

        pw.write(ret + "}\n\n");

        RegisterCounter = 0;

        return null;

    }

    @Override
    public String visit(Expression n, String argu) throws Exception {

        String reg = n.f0.accept(this, argu);

        return reg;
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
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    @Override
    public String visit(AllocationExpression n, String argu) throws Exception {
        String className = n.f1.accept(this, null);

        // remove the ":GETCLASS:" part from argu so that the following accept calls can
        // work [NOT NEEDED HERE SINCE ONLY IDENTIFIER IS ACCEPTED]
        boolean concat_type = false;
        if (argu.endsWith(":GETCLASS:")) {
            argu = argu.split(":")[0];
            concat_type = true;
        }

        // use class size calculated in Part 2 and add 8 to include vtable pointer
        Integer csize = iv.classSize.get(className) + 8;
        String r = String.format("call i8* @calloc (i32 1, i32 %d)", csize);
        String allocreg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        pw.write("\t" + allocreg + " = " + r + "\n");
        String bitcastreg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        pw.write("\t" + bitcastreg + " = bitcast i8* " + allocreg + " to i8***\n");

        String vtablereg = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String vtable_name = String.format("@.%s_vtable", className);

        Integer vtable_size = (vt.methodsIR.containsKey(className)) ? (vt.methodsIR.get(className).size()) : (0);

        String getelptr = String.format("\t%s = getelementptr [%d x i8*], [%d x i8*]* %s, i32 0, i32 0\n", vtablereg,
                vtable_size,
                vtable_size, vtable_name);

        pw.write(getelptr);

        String store = String.format("\tstore i8** %s, i8*** %s\n", vtablereg, bitcastreg);

        pw.write(store);

        if (concat_type) {
            return String.format("%s:%s", allocreg, className);
        }

        return allocreg;
    }

    @Override
    public String visit(ArrayAllocationExpression n, String argu) throws Exception {
        String reg = n.f0.accept(this, argu);
        return reg;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    @Override
    public String visit(ArrayAssignmentStatement n, String argu) throws Exception {

        String[] class_fn = argu.split("\\+");
        String classname = class_fn[0];
        String fnName = class_fn[1];

        // array type
        String type;

        String temp = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String array_ = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String id = n.f0.accept(this, null);
        // has pointer to array type
        String addr_reg = n.f0.accept(this, classname + "+" + fnName + ":GETADDRESS:");
        String rhs = n.f5.accept(this, argu);
        String index = n.f2.accept(this, argu);

        if (iv.fnVariables.containsKey(classname + "+" + fnName)
                && iv.fnVariables.get(classname + "+" + fnName).containsKey(id))
            type = type_converter(iv.fnVariables.get(classname + "+" + fnName).get(id));
        else {

            if (iv.classFields.containsKey(classname) == false
                    || iv.classFields.get(classname).containsKey(id) == false) {
                String superc = superClassHasFields(classname, id);
                type = type_converter(iv.classFields.get(superc).get(id));
            } else {

                type = type_converter(iv.classFields.get(classname).get(id));
            }

        }
        String indexed_type;

        String unit;

        // the returned register is of type array**. dereference it once to get the
        // array pointer
        if (type.equals("%_intarr*")) {
            indexed_type = "%_intarr";
            String r = String.format("%%_%s", Integer.toString(RegisterCounter++));
            String load = String.format("%s = load %%_intarr*, %%_intarr** %s\n", r, addr_reg);
            pw.write("\t" + load);
            unit = "i32";
            addr_reg = r;
        } else {
            indexed_type = "%_boolarr";
            String r = String.format("%%_%s", Integer.toString(RegisterCounter++));
            String load = String.format("%s = load %%_boolarr*, %%_boolarr** %s\n", r, addr_reg);
            pw.write("\t" + load);
            unit = "i1";
            addr_reg = r;
        }

        // oob check

        String addr_reg2 = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String size_addr = String.format("%s = getelementptr %s, %s %s, i32 0, i32 0\n", addr_reg2, indexed_type,
                type, addr_reg);

        String size = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String load_size = String.format("%s = load i32, i32* %s\n", size, addr_reg2);

        pw.write("\t" + size_addr);
        pw.write("\t" + load_size);

        String oob = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String invalid_index = String.format("%s = icmp ule i32 %s, %s\n", oob, size, index);
        String OOBLabel1 = String.format("oob%s", Integer.toString(OOBCounter++));
        String OOBLabel2 = String.format("oob%s", Integer.toString(OOBCounter++));

        pw.write("\t" + invalid_index);
        String br = String.format("br i1 %s, label %%%s, label %%%s\n", oob, OOBLabel1, OOBLabel2);

        pw.write("\t" + br);
        pw.write(OOBLabel1 + ":\n");
        pw.write("\tcall void @throw_oob()\n");
        pw.write("\tbr label %" + OOBLabel2 + "\n");
        pw.write(OOBLabel2 + ":\n");

        //

        String get_addr = String.format("%s = getelementptr %s, %s %s, i32 0, i32 1, i32 %s\n", temp, indexed_type,
                type, addr_reg,
                index);
        String temp2 = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String store = String.format("store %s %s, %s* %s\n", unit, rhs, unit, temp);

        pw.write("\t" + get_addr);

        pw.write("\t" + store);
        return null;

    }

    @Override
    public String visit(ArrayLength n, String argu) throws Exception {
        // get array type from the first primary expression
        String typ_reg = n.f0.accept(this, argu + ":GETARR:");
        String[] tr = typ_reg.split(":");
        String reg = tr[0];
        String type = tr[1];
        String indexed_type;
        if (type.equals("%_intarr*")) {
            indexed_type = "%_intarr";
        } else {
            indexed_type = "%_boolarr";
        }
        String len_addr = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String len = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String getLen_addr = String.format("%s = getelementptr %s, %s %s, i32 0, i32 0\n", len_addr, indexed_type, type,
                reg);
        String getLen = String.format("%s = load i32, i32* %s\n", len, len_addr);
        pw.write("\t" + getLen_addr);
        pw.write("\t" + getLen);
        return len;
    }

    @Override
    public String visit(BooleanArrayAllocationExpression n, String argu) throws Exception {
        String reg_t = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String expr_r_forStruct = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String write_arrSize = String.format("%%_%s", Integer.toString(RegisterCounter++));

        // allocate a struct with at least as many bytes to hold the array, plus the 4
        // bytes for the array size

        // remove the ":GETARR:" part from argu so that the following accept calls can
        // work
        boolean concat_type = false;
        if (argu.endsWith(":GETARR:")) {
            argu = argu.split(":")[0];
            concat_type = true;
        }

        String size_reg = n.f3.accept(this, argu);

        // oob check
        String oob = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String negcheck = String.format("%s = icmp slt i32 %s, 0\n", oob, size_reg);
        String OOBLabel1 = String.format("oob%s", Integer.toString(OOBCounter++));
        String OOBLabel2 = String.format("oob%s", Integer.toString(OOBCounter++));

        pw.write("\t" + negcheck);
        String br = String.format("br i1 %s, label %%%s, label %%%s\n", oob, OOBLabel1, OOBLabel2);

        pw.write("\t" + br);
        pw.write(OOBLabel1 + ":\n");
        pw.write("\tcall void @throw_oob()\n");
        pw.write("\tbr label %" + OOBLabel2 + "\n");
        pw.write(OOBLabel2 + ":\n");

        //

        String add_4 = String.format("%s = add i32 %s, 4 ", expr_r_forStruct, size_reg);

        // allocate the number of 1 bit objects reg holds
        String r = String.format("call i8* @calloc (i32 %s, i32 1)", expr_r_forStruct);
        pw.write("\t" + add_4 + "\n");
        pw.write("\t" + reg_t + " = " + r + "\n");
        pw.write("\t" + reg + " = " + "bitcast i8* " + reg_t + " to %_boolarr*\n");
        // get address of the struct's size
        pw.write("\t" + write_arrSize + " = getelementptr %_boolarr, %_boolarr* " + reg + ", i32 0, i32 0\n");
        pw.write("\t store i32 " + size_reg + ", i32* " + write_arrSize + "\n");

        if (concat_type) {
            return String.format("%s:%s", reg, "%_boolarr*");
        }

        return reg;
    }

    @Override
    public String visit(IntegerArrayAllocationExpression n, String argu) throws Exception {
        String reg_t = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String expr_r_forStruct = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String write_arrSize = String.format("%%_%s", Integer.toString(RegisterCounter++));

        // allocate a struct with at least as many bytes to hold the array, plus the 4
        // bytes for the array size

        // remove the ":GETARR:" part from argu so that the rest of the accept calls can
        // work
        boolean concat_type = false;
        if (argu.endsWith(":GETARR:")) {
            argu = argu.split(":")[0];
            concat_type = true;
        }

        String size_reg = n.f3.accept(this, argu);

        // oob check
        String oob = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String negcheck = String.format("%s = icmp slt i32 %s, 0\n", oob, size_reg);
        String OOBLabel1 = String.format("oob%s", Integer.toString(OOBCounter++));
        String OOBLabel2 = String.format("oob%s", Integer.toString(OOBCounter++));

        pw.write("\t" + negcheck);
        String br = String.format("br i1 %s, label %%%s, label %%%s\n", oob, OOBLabel1, OOBLabel2);

        pw.write("\t" + br);
        pw.write(OOBLabel1 + ":\n");
        pw.write("\tcall void @throw_oob()\n");
        pw.write("\tbr label %" + OOBLabel2 + "\n");
        pw.write(OOBLabel2 + ":\n");

        //

        String add_4 = String.format("%s = add i32 %s, 4 ", expr_r_forStruct, size_reg);

        // allocate the number of 1 bit objects reg holds
        String r = String.format("call i8* @calloc (i32 %s, i32 4)", expr_r_forStruct);

        pw.write("\t" + add_4 + "\n");
        pw.write("\t" + reg_t + " = " + r + "\n");
        pw.write("\t" + reg + " = " + "bitcast i8* " + reg_t + " to %_intarr*\n");
        // get address of the struct's size
        pw.write("\t" + write_arrSize + " = getelementptr %_intarr, %_intarr* " + reg + ", i32 0, i32 0\n");
        pw.write("\t store i32 " + size_reg + ", i32* " + write_arrSize + "\n");

        if (concat_type) {
            return String.format("%s:%s", reg, "%_intarr*");
        }

        return reg;
    }

    @Override
    public String visit(BracketExpression n, String argu) throws Exception {
        // type of the bracket expression = same as the expression inside of it
        return n.f1.accept(this, argu);
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
        String reg = n.f0.accept(this, argu);
        return reg;
    }

    public String visit(IntegerLiteral n, String argu) throws Exception {

        String val = String.format("%%_%s", Integer.toString(RegisterCounter++));
        pw.write(String.format("\t%s = add i32 0, %s\n\n", val, n.f0.toString()));
        return val;

    }

    public String visit(TrueLiteral n, String argu) throws Exception {
        String val = String.format("%%_%s", Integer.toString(RegisterCounter++));
        pw.write(String.format("\t%s = add i1 0, true\n\n", val));
        return val;
    }

    public String visit(FalseLiteral n, String argu) throws Exception {
        String val = String.format("%%_%s", Integer.toString(RegisterCounter++));
        pw.write(String.format("\t%s = add i1 0, false\n\n", val));
        return val;
    }

    public String visit(ThisExpression n, String argu) throws Exception {
        return "%this";
    }

    @Override
    public String visit(Clause n, String argu) throws Exception {
        return n.f0.accept(this, argu);
    }

    @Override
    public String visit(NotExpression n, String argu) throws Exception {
        String reg = n.f1.accept(this, argu);
        String reg2 = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String getReverse = String.format("%s = xor i1 %s, true\n", reg2, reg);
        pw.write("\t" + getReverse);
        return reg2;
    }

    @Override
    public String visit(AndExpression n, String argu) throws Exception {

        String l1 = String.format("lb%s", Integer.toString(IfCounter++));
        String l2 = String.format("lb%s", Integer.toString(IfCounter++));
        String l3 = String.format("lb%s", Integer.toString(IfCounter++));
        String l4 = String.format("lb%s", Integer.toString(IfCounter++));

        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String e1 = n.f0.accept(this, argu);

        pw.write(String.format("\tbr label %%%s\n", l1));
        pw.write(l1 + ":\n");

        pw.write(String.format("\tbr i1 %s, label %%%s, label %%%s\n", e1, l2, l3));

        pw.write(l2 + ":\n");

        String e2 = n.f2.accept(this, argu);

        pw.write(String.format("\tbr label %%%s\n", l3));
        pw.write(l3 + ":\n");
        pw.write(String.format("\tbr label %%%s\n", l4));
        pw.write(l4 + ":\n");
        pw.write(String.format("\t%s = phi i1 [%s, %%%s], [%s,%%%s]\n", reg, e1, l1, e2, l3));

        return reg;
    }

    @Override
    public String visit(CompareExpression n, String argu) throws Exception {

        String e1 = n.f0.accept(this, argu);
        String e2 = n.f2.accept(this, argu);
        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String cmp = String.format("icmp slt i32 %s, %s\n", e1, e2);
        pw.write(String.format("\t%s = %s", reg, cmp));
        return reg;
    }

    @Override
    public String visit(PlusExpression n, String argu) throws Exception {
        String e1 = n.f0.accept(this, argu);
        String e2 = n.f2.accept(this, argu);
        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String add = String.format("add i32 %s, %s\n", e1, e2);
        pw.write(String.format("\t%s = %s", reg, add));
        return reg;
    }

    @Override
    public String visit(MinusExpression n, String argu) throws Exception {
        String e1 = n.f0.accept(this, argu);
        String e2 = n.f2.accept(this, argu);
        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String minus = String.format("sub i32 %s, %s\n", e1, e2);
        pw.write(String.format("\t%s = %s", reg, minus));
        return reg;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    @Override
    public String visit(ArrayLookup n, String argu) throws Exception {
        // pass arr type to get the type along with register

        String typ_reg = n.f0.accept(this, argu + ":GETARR:");
        String[] tr = typ_reg.split(":");
        String reg = tr[0];
        String type = tr[1];

        // get the dereferenced pointer type
        String indexed_type;
        if (type.equals("%_boolarr*")) {
            indexed_type = "%_boolarr";
        } else
            indexed_type = "%_intarr";

        String index = n.f2.accept(this, argu);

        // oob check

        String addr_reg2 = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String size_addr = String.format("%s = getelementptr %s, %s %s, i32 0, i32 0\n", addr_reg2, indexed_type,
                type, reg);

        String size = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String load_size = String.format("%s = load i32, i32* %s\n", size, addr_reg2);

        pw.write("\t" + size_addr);
        pw.write("\t" + load_size);

        String oob = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String invalid_index = String.format("%s = icmp ule i32 %s, %s\n", oob, size, index);
        String OOBLabel1 = String.format("oob%s", Integer.toString(OOBCounter++));
        String OOBLabel2 = String.format("oob%s", Integer.toString(OOBCounter++));

        pw.write("\t" + invalid_index);
        String br = String.format("br i1 %s, label %%%s, label %%%s\n", oob, OOBLabel1, OOBLabel2);

        pw.write("\t" + br);
        pw.write(OOBLabel1 + ":\n");
        pw.write("\tcall void @throw_oob()\n");
        pw.write("\tbr label %" + OOBLabel2 + "\n");
        pw.write(OOBLabel2 + ":\n");

        //

        String temp = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String get_item_address = String.format("getelementptr %s,%s %s, i32 0,i32 1, i32 %s\n", indexed_type, type,
                reg,
                index);
        String item_ = String.format("%s = %s", temp, get_item_address);
        pw.write("\t" + item_);

        // if _boolarr unit is i1 else i32
        String unit_type;

        if (type.equals("%_boolarr*")) {
            unit_type = "i1";
        } else {
            unit_type = "i32";
        }

        String temp2 = String.format("%%_%s", Integer.toString(RegisterCounter++));

        String get_object = String.format("load %s, %s* %s\n", unit_type, unit_type, temp);
        String load = String.format("%s = %s", temp2, get_object);
        pw.write("\t" + load);
        return temp2;

    }

    @Override
    public String visit(TimesExpression n, String argu) throws Exception {
        String e1 = n.f0.accept(this, argu);
        String e2 = n.f2.accept(this, argu);
        String reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
        String multip = String.format("mul i32 %s, %s\n", e1, e2);
        pw.write(String.format("\t%s = %s", reg, multip));
        return reg;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    @Override
    public String visit(VarDeclaration n, String argu) throws Exception {
        // get type itself, not identifier
        String _type = n.f0.accept(this, "type");
        String ret_dec = String.format("\t%%%s = alloca %s\n", n.f1.accept(this, null), _type);
        pw.write(ret_dec);
        return null;
    }

    @Override
    public String visit(MainClass n, String argu) throws Exception {

        String classname = n.f1.accept(this, null);

        pw.write("define i32 @main() {\n");

        for (Node vdecl : n.f14.nodes) {
            vdecl.accept(this, classname);
        }
        for (Node stmt : n.f15.nodes) {
            stmt.accept(this, classname + "+" + "main");
        }

        pw.write("\tret i32 0 \n}\n");
        // reset counter
        RegisterCounter = 0;
        return null;
    }

    @Override
    public String visit(Block n, String argu) throws Exception {
        for (Node st : n.f1.nodes) {
            st.accept(this, argu);
        }
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    @Override
    public String visit(AssignmentStatement n, String argu) throws Exception {

        String[] class_fn = argu.split("\\+");
        String classname = class_fn[0];
        String fnName = class_fn[1];

        String expr_r = n.f2.accept(this, argu);

        // get register with address of the id
        String addr_reg = n.f0.accept(this, classname + "+" + fnName + ":GETADDRESS:");
        String id = n.f0.accept(this, null);

        String idType;

        if (iv.fnVariables.containsKey(classname + "+" + fnName) && iv.fnVariables.get(classname +
                "+" + fnName).containsKey(id)) {
            idType = type_converter(iv.fnVariables.get(classname + "+" + fnName).get(id));
        } else {
            // check if the field is from the superclass
            if (iv.classFields.containsKey(classname) == false
                    || iv.classFields.get(classname).containsKey(id) == false) {
                String superc = superClassHasFields(classname, id);
                idType = type_converter(iv.classFields.get(superc).get(id));
            } else {

                idType = type_converter(iv.classFields.get(classname).get(id));
            }
        }

        String store = String.format("\tstore %s %s, %s* %s\n", idType, expr_r, idType, addr_reg);

        pw.write(store);
        return null;

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

        String boolreg = n.f2.accept(this, argu);
        String if_label = "if" + Integer.toString(IfCounter++);
        String else_label = "if" + Integer.toString(IfCounter++);
        String escape_label = "if" + Integer.toString(IfCounter++);

        String branch = String.format("\tbr i1 %s,label %%%s, label %%%s\n", boolreg, if_label, else_label);
        pw.write(branch);
        pw.write(if_label + ":\n");
        n.f4.accept(this, argu);
        pw.write(String.format("\tbr label %%%s\n", escape_label));
        pw.write(else_label + ":\n");
        n.f6.accept(this, argu);
        pw.write(String.format("\tbr label %%%s\n", escape_label));
        pw.write(escape_label + ":\n");
        return null;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    @Override
    public String visit(WhileStatement n, String argu) throws Exception {

        String enter_loop = "loop" + Integer.toString(LoopCounter++);
        String escape_label = "loop" + Integer.toString(LoopCounter++);

        String loop_check_label = "loop" + Integer.toString(LoopCounter++);

        String jump_back = String.format("br label %%%s\n", loop_check_label);

        pw.write("\tbr label %" + loop_check_label + "\n");
        pw.write("\t" + loop_check_label + ":\n");

        String boolreg = n.f2.accept(this, argu);
        String branch = String.format("br i1 %s,label %%%s, label %%%s\n", boolreg, enter_loop, escape_label);

        pw.write("\t" + branch);
        pw.write("\t" + enter_loop + ":\n");
        n.f4.accept(this, argu);
        pw.write("\t" + jump_back);
        pw.write("\t" + escape_label + ":\n");
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

        for (Node m : n.f4.nodes) {
            m.accept(this, classname);
        }
        return null;
    }

    @Override
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        for (Node m : n.f6.nodes) {
            m.accept(this, classname);
        }
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

        if (argu == null)
            return n.f0.toString();

        if (argu.equals("type")) {
            // return i8* instead of class name in cases where it's needed
            return "i8*";
        } else {

            String id = n.f0.toString();

            // extra attributes to only get the object's address (for when assigning to it),
            // to get the array's type (when assigning to it), to get the object's class
            // name (for method call)
            String[] class_fn;
            if (argu.endsWith(":GETARR:") || argu.endsWith(":GETADDRESS:") || argu.endsWith(":GETCLASS:")) {
                class_fn = argu.split(":")[0].split("\\+");
            } else {
                class_fn = argu.split("\\+");
            }

            String classname = class_fn[0];
            String fnName = class_fn[1];
            String type;
            String reg;
            String reg2 = String.format("%%_%s", Integer.toString(RegisterCounter++));
            if (iv.fnVariables.containsKey(classname + "+" + fnName)
                    && iv.fnVariables.get(classname + "+" + fnName).containsKey(id)) {

                // return type of object + register of local variable, so something like i32:%x
                reg = String.format("%%%s", id);
                type = type_converter(iv.fnVariables.get(classname + "+" + fnName).get(id));

            } else {
                Integer idOffset = 0;
                // if class doesnt have the ID then look in the superclass
                if (iv.classFields.containsKey(classname) == false
                        || iv.classFields.get(classname).containsKey(id) == false) {
                    String superc = superClassHasFields(classname, id);
                    idOffset = iv.offsetFields.get(superc).get(id) + 8;
                    type = type_converter(iv.classFields.get(superc).get(id));
                } else {
                    idOffset = iv.offsetFields.get(classname).get(id) + 8;
                    type = type_converter(iv.classFields.get(classname).get(id));
                }
                reg = String.format("%%_%s", Integer.toString(RegisterCounter++));
                String getaddr = String.format("getelementptr i8, i8* %%this, i32 %d\n", idOffset);
                String getObj = String.format("%s = %s\n", reg, getaddr);
                pw.write("\t" + getObj);
                String reg_cast = String.format("%%_%s", Integer.toString(RegisterCounter++));
                // if the function is not a local variable, it is always a class field -
                // therefore store its address
                String _cast = String.format("%s = bitcast i8* %s to %s*\n", reg_cast, reg, type);
                pw.write("\t" + _cast);
                reg = reg_cast;
            }

            if (argu.endsWith(":GETADDRESS:")) {
                // only get object's address (for assignments)
                return reg;
            } else if (argu.endsWith(":GETARR:")) {
                // append array type or classname if asked
                String t = String.format("%%_%s", Integer.toString(RegisterCounter++));
                String load = String.format("%s = load %s, %s* %s\n", t, type, type, reg);
                pw.write("\t" + load);
                return String.format("%s:%s", t, type);
            } else if (argu.endsWith(":GETCLASS:")) {
                String classtype = "";
                if (iv.fnVariables.containsKey(classname + "+" + fnName)
                        && iv.fnVariables.get(classname + "+" + fnName).containsKey(id))
                    classtype = iv.fnVariables.get(classname + "+" + fnName).get(id);
                else {
                    if (iv.classFields.containsKey(classname) == false
                            || iv.classFields.get(classname).containsKey(id) == false) {
                        String superc = superClassHasFields(classname, id);
                        classtype = iv.classFields.get(superc).get(id);
                    } else {

                        classtype = iv.classFields.get(classname).get(id);
                    }
                }
                String t = String.format("%%_%s", Integer.toString(RegisterCounter++));
                String load = String.format("%s = load %s, %s* %s\n", t, type, type, reg);
                pw.write("\t" + load);
                return String.format("%s:%s", t, classtype);
            }

            // get the object itself
            String t = String.format("%%_%s", Integer.toString(RegisterCounter++));
            String obj = String.format("%s = load %s, %s* %s\n", t, type, type, reg);
            pw.write("\t" + obj);

            return t;
        }

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
        // get the IR form
        String type = n.f0.accept(this, "type");
        // get the name
        String id = n.f1.accept(this, null);

        return String.format("%s %%.%s", type, id);
    }

}
