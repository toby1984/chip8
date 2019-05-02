package de.codesourcery.chip8.emulator;

import java.util.Objects;

public final class Breakpoint
{
    public final int address;
    public final boolean isTemporary;

    public Breakpoint(int address, boolean isTemporary)
    {
        this.address = address;
        this.isTemporary = isTemporary;
    }

    @Override
    public boolean equals(Object o)
    {
        if ( o instanceof Breakpoint) {
            return this.address == ((Breakpoint) o).address &&
                    this.isTemporary == ((Breakpoint) o).isTemporary;
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( address, isTemporary );
    }

    @Override
    public String toString()
    {
        if ( isTemporary ) {
            return "Temporary breakpoint @ 0x"+Integer.toHexString(address);
        }
        return "Breakpoint @ 0x"+Integer.toHexString(address);
    }
}
