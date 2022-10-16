import syntaxtree.*;
import visitor.*;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect.Type;
import java.util.*;

class SemanticsAnalyzer {
    String filename;
    InitVisitor iv;

    SemanticsAnalyzer(String f) {
        filename = f;
        iv = null;
    }

    public InitVisitor getVisitor() throws Exception {
        FileInputStream fis = null, fis_d = null;
        InitVisitor eval = null;

        fis = new FileInputStream(filename);
        MiniJavaParser parser = new MiniJavaParser(fis);

        Goal root = parser.Goal();
        eval = new InitVisitor();
        root.accept(eval, null);
        // return visitor so that i can extract offsets directly from it

        try {
            if (fis != null)
                fis.close();
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
        }

        this.iv = eval;
        return eval;

    }

    public void type_check() throws Exception {
        FileInputStream fis = null, fis_d = null;
        TypeCheckingVisitor eval;

        fis = new FileInputStream(filename);
        MiniJavaParser parser = new MiniJavaParser(fis);

        Goal root = parser.Goal();

        eval = new TypeCheckingVisitor(this.iv);
        root.accept(eval, null);

    }

}
