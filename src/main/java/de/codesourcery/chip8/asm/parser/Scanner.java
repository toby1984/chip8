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

/**
 * Source code scanner.
 *
 * Iterates over the input source code one character at a time.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Scanner
{
    private final String input;
    private int index;

    public Scanner(String input) {
        this.input = input;
    }

    public String getText() {
        return input;
    }

    private void assertNotEOF()
    {
        if ( eof() ) {
            throw new IllegalStateException("Already at EOF");
        }
    }

    public boolean eof() {
        return index >= input.length();
    }

    public void back() throws IllegalStateException
    {
        if ( index <= 0 )
        {
            throw new IllegalStateException("Already at start of input");
        }
        index--;
    }

    public char next()
    {
        assertNotEOF();
        return input.charAt(index++);
    }

    public char peek() {
        assertNotEOF();
        return input.charAt(index);
    }

    public int offset() {
        return index;
    }

    public void setOffset(int offset) {
        this.index=offset;
    }
}
