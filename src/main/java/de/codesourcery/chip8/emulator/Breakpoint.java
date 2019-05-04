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
package de.codesourcery.chip8.emulator;

import java.util.Objects;

/**
 * A breakpoint.
 *
 * Breakpoints come in two flavors, temporary and permanent ones.
 *
 * A temporary breakpoint will be automatically removed as soon as it gets hit. This
 * is used to implement the 'step over' debugger feature.
 *
 * @author tobias.gierke@code-sourcery.de
 */
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
