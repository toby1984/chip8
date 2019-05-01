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

import java.util.Objects;
import java.util.regex.Pattern;

public class Identifier
{
    public final String value;
    private static final Pattern ID = Pattern.compile("[_a-zA-Z]+[_0-9a-zA-Z]*");

    public Identifier(String value)
    {
        if ( ! isValid(value) ) {
            throw new IllegalArgumentException("Not a valid identifier: "+value);
        }
        this.value = value;
    }

    public static boolean isValid(String value)
    {
        return value != null && ID.matcher( value ).matches();
    }

    @Override
    public String toString()
    {
        return value;
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
}
