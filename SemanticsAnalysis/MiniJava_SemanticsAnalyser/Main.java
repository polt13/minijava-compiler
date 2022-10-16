import syntaxtree.*;
import visitor.*;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        FileInputStream fis = null, fis_d = null;
        for (int i = 0; i < args.length; i++) {
            try {
                fis = new FileInputStream(args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);

                Goal root = parser.Goal();

                InitVisitor eval = new InitVisitor();
                root.accept(eval, null);

                ///////////////////////////////////////////////////////////

                fis_d = new FileInputStream(args[i]);
                MiniJavaParser parser_d = new MiniJavaParser(fis_d);
                Goal root_d = parser_d.Goal();

                TypeCheckingVisitor semantics_chk = new TypeCheckingVisitor(eval);
                root_d.accept(semantics_chk, null);

                eval.printOffsets();

            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
            } finally {
                try {
                    if (fis != null)
                        fis.close();
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
            }

        }
    }
}
