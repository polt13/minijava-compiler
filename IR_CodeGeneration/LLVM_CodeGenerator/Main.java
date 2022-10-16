import syntaxtree.*;
import visitor.*;

import java.io.*;

import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {

        String helper_functions = "";
        BufferedReader reader = new BufferedReader(new FileReader("helper.help"));
        // in order copy helper functions to target
        while (reader.ready()) {
            helper_functions += reader.readLine() + "\n";
        }

        for (int i = 0; i < args.length; i++) {
            // visitor for file
            SemanticsAnalyzer analyzer = new SemanticsAnalyzer(args[i]);
            // return visitor that just got populated, so that i can access the offsets
            // themselves

            InitVisitor _vst;

            try {

                _vst = analyzer.getVisitor();
                analyzer.type_check();

            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                continue;
            }

            // which file to parse
            FileInputStream fis = new FileInputStream(args[i]);

            // which file to write the output IR to
            FileOutputStream output_str = new FileOutputStream(args[i].split("\\.")[0] + ".ll");

            PrintWriter target_write = new PrintWriter(output_str);

            MiniJavaParser parser = new MiniJavaParser(fis);

            target_write.write("%_boolarr = type { i32, [0 x i1] }\n%_intarr = type { i32, [0 x i32] }\n");

            VTable_Visitor vt = new VTable_Visitor(target_write);

            Goal root = parser.Goal();

            root.accept(vt, null);

            target_write.write("\n" + helper_functions + "\n");

            //////////////////////////////////////////////////

            Generator_Visitor gv = new Generator_Visitor(target_write, _vst, vt);
            root.accept(gv, null);

            try {
                if (fis != null)
                    fis.close();
                if (target_write != null) {
                    // close writer so that the buffer is sent to file..
                    target_write.close();
                }
            } catch (IOException ex) {
                System.err.println(ex.getMessage());
            }

        }

    }
}