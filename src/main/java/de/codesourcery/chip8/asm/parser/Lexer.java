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
package de.codesourcery.chip8.asm.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lexer
{
    private static final Pattern BINARY_NUMBER = Pattern.compile("%[01]+");
    private static final Pattern DECIMAL_NUMBER = Pattern.compile("[0-9]+");
    private static final Pattern HEX_NUMBER = Pattern.compile("0[xX][0-9a-fA-F]+");
    private static final Pattern REGISTER = Pattern.compile("[vV][0-9a-fA-F]+");

    private final List<Token> tokens = new ArrayList<>();

    private boolean skipWhitespace=true;
    private final StringBuilder buffer = new StringBuilder();

    private final Scanner scanner;

    public Lexer(Scanner scanner) {
        this.scanner = scanner;
    }

    public Token peek()
    {
        if ( tokens.isEmpty() ) {
            parse();
        }
        return tokens.get(0);
    }

    public Token next() {
        if ( tokens.isEmpty() ) {
            parse();
        }
        return tokens.remove(0);
    }

    public boolean eof()
    {
        return peek().is( TokenType.EOF);
    }

    private boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }

    private void parse()
    {
        while ( skipWhitespace && ! scanner.eof() && isWhitespace( scanner.peek() ) ) {
            scanner.next();
        }
        buffer.setLength( 0 );
        int startOffset = scanner.offset();
        while ( ! scanner.eof() )
        {
            char c = scanner.peek();
            if ( isWhitespace( c ) )
            {
                parseBuffer(startOffset);
                if ( ! skipWhitespace ) {
                    scanner.next();
                    tokens.add( new Token( TokenType.WHITESPACE, c, scanner.offset() ) );
                }
                return;
            }
            switch( c )
            {
                case '\n':
                    parseBuffer(startOffset);
                    scanner.next();
                    tokens.add( new Token( TokenType.NEWLINE, c, scanner.offset() ) );
                    return;
                case ',':
                    parseBuffer(startOffset);
                    scanner.next();
                    tokens.add( new Token( TokenType.COMMA, c, scanner.offset() ) );
                    return;
                case ':':
                    parseBuffer(startOffset);
                    scanner.next();
                    tokens.add( new Token( TokenType.COLON, c, scanner.offset() ) );
                    return;
                case ';':
                    parseBuffer(startOffset);
                    scanner.next();
                    tokens.add( new Token( TokenType.SEMICOLON, c, scanner.offset() ) );
                    return;
            }
            buffer.append( scanner.next() );
        }
        parseBuffer(startOffset);
        if ( scanner.eof() ) {
            tokens.add( new Token( TokenType.EOF, "", scanner.offset() ) );
        }
    }

    private void parseBuffer(int startOffset)
    {
        if ( buffer.length() == 0 ) {
            return;
        }
        final String s = buffer.toString();

        // register
        Matcher m = REGISTER.matcher( s );
        if ( m.matches() )
        {
            tokens.add( new Token( TokenType.REGISTER,s,startOffset) );
            return;
        }

        // hex number
        m = HEX_NUMBER.matcher( s );
        if ( m.matches() )
        {
            tokens.add( new Token( TokenType.HEX_NUMBER,s,startOffset) );
            return;
        }

        // decimal number
        m = DECIMAL_NUMBER.matcher( s );
        if ( m.matches() )
        {
            tokens.add( new Token( TokenType.DECIMAL_NUMBER,s,startOffset) );
            return;
        }
        m = BINARY_NUMBER.matcher( s );
        if ( m.matches() )
        {
            tokens.add( new Token( TokenType.BINARY_NUMBER,s,startOffset) );
            return;
        }
        tokens.add( new Token( TokenType.TEXT,s,startOffset) );
    }

    public void pushBack(Token token)
    {
        this.tokens.add(0,token);
    }

    public void setSkipWhitespace(boolean newState)
    {
        this.skipWhitespace = skipWhitespace;
        if ( ! tokens.isEmpty() ) {
            scanner.setOffset( tokens.get(0).offset );
            tokens.clear();
        }
    }

    public Scanner getScanner()
    {
        return scanner;
    }
}