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
        context = new Assembler.CompilationContext( new ExecutableWriter(new ByteArrayOutputStream()) );
    }

    @Test
    public void testBitwiseXor() {
        assertEquals( 0b101 ^ 0b010, ExpressionEvaluator.evaluate( parse( "%101 ^ %010" ), context, true ) );
    }

    @Test
    public void testBitwiseAnd() {
        assertEquals( 0b111 & 0b010, ExpressionEvaluator.evaluate( parse( "%111 & %010" ), context, true ) );
    }

    @Test
    public void testBitwiseOr() {
        assertEquals( 0b101 | 0b010, ExpressionEvaluator.evaluate( parse( "%101 | %010" ), context, true ) );
    }

    @Test
    public void testShiftLeft() {
        assertEquals( 4<<2, ExpressionEvaluator.evaluate( parse( "4<<2" ), context, true ) );
    }

    @Test
    public void testShiftRight() {
        assertEquals( 8>>2, ExpressionEvaluator.evaluate( parse( "8>>2" ), context, true ) );
    }

    @Test
    public void testBitwiseNegation() {
        assertEquals( ~-1, ExpressionEvaluator.evaluate( parse( "~-1" ), context, true ) );
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
        final Lexer lexer = new Lexer( new Scanner( s ) );
        final ASTNode ast = new Parser( lexer, new Assembler.CompilationContext( new ExecutableWriter() ) ).parseExpression();
        System.out.println( ast.toPrettyString() );
        return ast;
    }
}