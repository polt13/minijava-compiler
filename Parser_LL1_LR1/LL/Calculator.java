import java.io.InputStream;
import java.io.IOException;


public class Calculator {

    private final InputStream in;

    private int lookahead;

    public Calculator(InputStream in) throws IOException {
        this.in = in;
        lookahead = in.read();
    }

    private void consume(int symbol) throws IOException, ParseError {
        if (lookahead == symbol) {
            lookahead = in.read();
        } else
            throw new ParseError();
    }

    private boolean isDigit(int c) {
        return '0' <= c && c <= '9';
    }
    private boolean isLeftParen(int c) {
        return c == '(';
    }
    private boolean isRightParen(int c){ return c == ')';}

    private int evalDigit(int c) {
        return c - '0';
    }

    public int Goal() throws IOException,ParseError{
        int val = Expr();

        if (lookahead != -1 && lookahead != '\n')
            throw new ParseError();

        return val;
    }

    private int Expr() throws IOException,ParseError{
       int term = Term();
       return Expr2(term);
    }

    private int Expr2(int condition) throws IOException,ParseError{

        switch(lookahead){
            case '^':
                consume('^');
                return condition ^ Expr();

            case ')':
            case -1:
            case '\n':
                return condition;
        }

        throw new ParseError();

    }

    private int Term() throws IOException,ParseError{
        int val = NumExpr();
        return Term2(val);

    }

    private int Term2(int condition) throws IOException,ParseError{
        switch(lookahead){
            case '&':
                consume('&');
                return condition & Term();

            case ')':
            case '^':
            case -1:
            case '\n':
                return condition;
        }

        throw new ParseError();

    }

    private int NumExpr() throws IOException,ParseError{
        if (isDigit(lookahead)){
            int cond = evalDigit((lookahead));
            consume((lookahead));
            return cond;
        }else if(isLeftParen(lookahead)){

            consume('(');
            int exp = Expr();

            if(isRightParen(lookahead)){

                consume(')');
                return exp;
            }

        }

        throw new ParseError();
    }




}