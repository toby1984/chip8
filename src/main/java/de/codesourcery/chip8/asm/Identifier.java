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
