package de.codesourcery.chip8.asm;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class LexerTest extends TestCase
{
    private Lexer lexer;

    public void testBlank()
    {
        List<Token> tokens = lex("     \n\n\n    ");
        assertEquals( "Got "+tokens,0,tokens.size());
    }

    public void testInstruction()
    {
        List<Token> tokens = lex("label: jp 0x123 ; comment");
        assertEquals( "Got "+tokens.stream().map(x->x.toString()).collect( Collectors.joining("\n" ) ),0,tokens.size());
    }

    public void test1() {

        List<Token> tokens = lex("test:");
        tokens.forEach( System.out::println );
    }

    private List<Token> lex(String s)
    {
        lexer = new Lexer(new Scanner(s));
        List<Token> list = new ArrayList<>();
        while(true){
            final Token tok = lexer.next();
            list.add(tok);
            if ( tok.is(TokenType.EOF) ) {
                break;
            }
        }
        return list;
    }
}
