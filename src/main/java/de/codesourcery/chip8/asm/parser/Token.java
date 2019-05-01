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

public final class Token
{
    public final TokenType type;
    public final String value;
    public final int offset;

    public Token(TokenType type, char value, int offset) {
        this(type,Character.toString(value),offset);
    }

    public Token(TokenType type, String value, int offset)
    {
        this.type = type;
        this.value = value;
        this.offset = offset;
    }

    public boolean is(TokenType type) {
        return type.equals( this.type );
    }

    @Override
    public String toString()
    {
        return "Token{" +
               "type=" + type +
               ", value='" + value + '\'' +
               ", offset=" + offset +
               '}';
    }
}
