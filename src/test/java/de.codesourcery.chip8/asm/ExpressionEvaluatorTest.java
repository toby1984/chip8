package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class ExpressionEvaluatorTest
{
    private ExpressionEvaluator.INodeEvaluator context;

    @Before
    public void setup()
    {
        context = new ExpressionEvaluator.NodeEvaluator(
                new Assembler.CompilationContext( new ExecutableWriter( new ByteArrayOutputStream() ) ) );
    }

    @Test
    public void testGreaterThan() {
        Assert.assertEquals( Boolean.TRUE, context.evaluate(parse("2 > 1"), true));
    }

    @Test
    public void testLessThan() {
        Assert.assertEquals( Boolean.TRUE, context.evaluate(parse("3 < 4"), true));
    }

    @Test
    public void testEquals() {
        Assert.assertEquals( Boolean.TRUE, context.evaluate(parse(" 3 == 3"), true));
    }

    @Test
    public void testNotEquals() {
        Assert.assertEquals( Boolean.TRUE, context.evaluate(parse(" 3 != 4"), true));
    }

    @Test
    public void testLogicalAnd() {
        Assert.assertEquals( Boolean.TRUE, context.evaluate(parse(" 1==1 && 2==2"), true));
    }

    @Test
    public void testLogicalOr() {
        Assert.assertEquals( Boolean.TRUE, context.evaluate(parse("1==2 || 3 < 4"), true));
    }

    @Test
    public void testBitwiseXor() {
        Assert.assertEquals( 0b101 ^ 0b010, context.evaluate(parse("%101 ^ %010"), true));
    }

    @Test
    public void testBitwiseAnd() {
        Assert.assertEquals( 0b111 & 0b010, context.evaluate(parse("%111 & %010"), true));
    }

    @Test
    public void testBitwiseOr() {
        Assert.assertEquals( 0b101 | 0b010, context.evaluate(parse("%101 | %010"), true));
    }

    @Test
    public void testShiftLeft() {
        Assert.assertEquals( 4<<2, context.evaluate(parse("4<<2"), true));
    }

    @Test
    public void testShiftRight() {
        Assert.assertEquals( 8>>2, context.evaluate(parse("8>>2"), true));
    }

    @Test
    public void testBitwiseNegation() {
        Assert.assertEquals( ~-1, context.evaluate(parse("~-1"), true));
    }

    @Test
    public void testOperatorPrecedence()
    {
        Assert.assertEquals(9, context.evaluate(parse("(1+2)*3"), true));
        Assert.assertEquals(7, context.evaluate(parse("1+2*3"), true));
    }

    @Test
    public void testUnaryOperator()
    {
        Assert.assertEquals(-255, context.evaluate(parse("- 0xff"), true));
        Assert.assertEquals(-3, context.evaluate(parse("(-3)"), true));
        Assert.assertEquals( 3, context.evaluate(parse("--3"), true));
    }

    private ASTNode parse(String s)
    {
        final Lexer lexer = new Lexer( new Scanner( s ) );
        final ASTNode ast = new Parser( lexer, new Assembler.CompilationContext( new ExecutableWriter() ) ).parseExpression();
        System.out.println( ast.toPrettyString() );
        return ast;
    }
}