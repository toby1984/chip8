package de.codesourcery.chip8.asm;

import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

public class ParserTest extends TestCase
{
    public void test()
    {
        ASTNode ast = parse( "label: add v0,v1 ; comment");
        ast.visitInOrder( (node,depth) -> System.out.println( StringUtils.repeat(' ', depth)+" " +node ));
    }

    private ASTNode parse(String source) {
        Parser p = new Parser( new Lexer( new Scanner( source ) ) );
        return p.parse();
    }
}
