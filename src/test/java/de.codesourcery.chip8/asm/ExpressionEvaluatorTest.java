package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

public class ExpressionEvaluatorTest
{
    private Assembler.CompilationContext context;

    @Before
    public void setup()
    {
        context = new Assembler.CompilationContext( new ByteArrayOutputStream() );
    }

    @Test
    public void testOperatorPrecedence()
    {
        assertEquals(9, ExpressionEvaluator.evaluate( parse( "(1+2)*3" ), context, true ) );
        assertEquals(7, ExpressionEvaluator.evaluate( parse( "1+2*3" ), context, true ) );
    }

    @Test
    public void testUnaryOperator()
    {
        assertEquals(-255, ExpressionEvaluator.evaluate( parse( "- 0xff" ), context, true ) );
        assertEquals(-3, ExpressionEvaluator.evaluate( parse( "(-3)" ), context, true ) );
        assertEquals( 3, ExpressionEvaluator.evaluate( parse( "--3" ), context, true ) );
    }

    private ASTNode parse(String s)
    {
        return new Parser( new Lexer( new Scanner( s ) ) ).parseExpression();
    }
}