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

import de.codesourcery.chip8.asm.ast.DirectiveNode;
import de.codesourcery.chip8.asm.parser.Parser;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An identifier.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Identifier
{
    // valid identifiers look like this
    private static final Pattern ID = Pattern.compile("[_a-zA-Z]+[_0-9a-zA-Z]*");

    // reserved identifiers
    private static final Set<String> RESERVED = new HashSet<>();

    static
    {
        reserved("i");
        reserved("k");
        reserved("dt");
        reserved("st");
        reserved("f");
        reserved("b");
        reserved("pc");
        reserved("[i]");

        for (DirectiveNode.Type t : DirectiveNode.Type.values() ) {
            reserved( t.keyword );
        }
        for ( Parser.Instruction insn : Parser.Instruction.values() ) {
            reserved( insn.mnemonic );
        }
    }

    private static void reserved(String s) {
        RESERVED.add( s.toLowerCase() );
    }

    public final String value;

    public Identifier(String value)
    {
        if ( ! isValid(value) ) {
            throw new IllegalArgumentException("Not a valid identifier: "+value);
        }
        this.value = value;
    }

    public static Identifier of(String s) {
        return new Identifier(s);
    }

    public static boolean isValid(String value)
    {
        return value != null &&
               ID.matcher( value ).matches() &&
               ! isReserved( value );
    }

    public static boolean isReserved(String s)
    {
        return RESERVED.contains( s.toLowerCase() );
    }

    @Override
    public boolean equals(Object o)
    {
        if ( o instanceof Identifier)
        {
            return this.value.equals( ((Identifier) o).value );
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return value.hashCode();
    }

    @Override
    public String toString()
    {
        return value;
    }
}
