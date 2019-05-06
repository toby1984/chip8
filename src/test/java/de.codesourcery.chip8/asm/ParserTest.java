/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.DirectiveNode;
import de.codesourcery.chip8.asm.ast.IdentifierNode;
import de.codesourcery.chip8.asm.ast.NumberNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

public class ParserTest extends TestCase
{
    public void testEqu()
    {
        ASTNode ast = parse( ".equ a = 1 ; test comment");
        ast.visitInOrder( (node,depth) -> System.out.println( StringUtils.repeat(' ', depth)+" " +node ));

        assertTrue( ast.child(0).child(0) instanceof DirectiveNode );

        final DirectiveNode dir = (DirectiveNode) ast.child(0).child(0);

        assertEquals( DirectiveNode.Type.EQU, dir.type );
        assertEquals( Identifier.of("a") , ((IdentifierNode) dir.child(0)).identifier );

        assertTrue( dir.child(1) instanceof NumberNode );
        assertEquals( 1 , ((NumberNode) dir.child(1)).value );
    }

    public void testAlias()
    {
        ASTNode ast = parse( ".alias v1 = x ; test comment");
        ast.visitInOrder( (node,depth) -> System.out.println( StringUtils.repeat(' ', depth)+" " +node ));

        assertTrue( ast.child(0).child(0) instanceof DirectiveNode );

        final DirectiveNode dir = (DirectiveNode) ast.child(0).child(0);

        assertEquals( DirectiveNode.Type.ALIAS, dir.type );
        assertEquals( 1 , ((RegisterNode) dir.child(0)).regNum );

        assertTrue( dir.child(1) instanceof IdentifierNode );
        assertEquals( Identifier.of("x") , ((IdentifierNode) dir.child(1)).identifier );
    }

    public void testSimpleStatement()
    {
        ASTNode ast = parse( "label: add v0,v1 ; comment");
        ast.visitInOrder( (node,depth) -> System.out.println( StringUtils.repeat(' ', depth)+" " +node ));
    }

    public void testStatementWithExpression()
    {
        ASTNode ast = parse( "label: ld v0, 2*(3+-1) ; comment");
        ast.visitInOrder( (node,depth) -> System.out.println( StringUtils.repeat(' ', depth)+" " +node ));
    }

    private ASTNode parse(String source) {
        Parser p = new Parser( new Lexer( new Scanner( source ) ) );
        return p.parse();
    }
}
