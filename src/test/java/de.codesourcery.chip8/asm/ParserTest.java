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
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;

public class ParserTest extends TestCase
{
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

    public void testAlias()
    {
        ASTNode ast = parse( ".alias x = v0");
        ast.visitInOrder( (node,depth) -> System.out.println( StringUtils.repeat(' ', depth)+" " +node ));
    }

    private ASTNode parse(String source) {
        Parser p = new Parser( new Lexer( new Scanner( source ) ) );
        return p.parse();
    }
}
