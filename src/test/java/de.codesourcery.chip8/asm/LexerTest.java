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

import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Scanner;
import de.codesourcery.chip8.asm.parser.Token;
import de.codesourcery.chip8.asm.parser.TokenType;
import junit.framework.TestCase;

import java.util.ArrayList;
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
            if ( tok.is( TokenType.EOF) ) {
                break;
            }
        }
        return list;
    }
}
