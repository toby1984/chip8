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
import java.util.function.Consumer;

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
    public static final IMatcher ALWAYS_TRUE = new IMatcher()
    {
        /**
         * Checks whether this breakpoint is actually hit.
         *
         * @param driver
         * @return
         */
        @Override
        public boolean matches(EmulatorDriver driver)
        {
            return true;
        }

        /**
         * Returns this matcher's expression for display on the UI.
         *
         * @return matcher expression. Two matchers with the same expression are always considered to be
         * equal/interchangeable.
         */
        @Override
        public String getExpression()
        {
            return "";
        }
    };

    public final int address;
    public final boolean isTemporary;
    public final IMatcher matcher;

    /**
     * Matcher used to check secondary conditions (register contents etc) before
     * a breakpoint is declared as being 'hit'.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    public interface IMatcher
    {
        boolean matches(EmulatorDriver driver);

        String getExpression();
    }

    public Breakpoint(int address, boolean isTemporary) {
        this(address,isTemporary,ALWAYS_TRUE);
    }

    public Breakpoint(int address, boolean isTemporary,IMatcher matcher)
    {
        this.address = address;
        this.isTemporary = isTemporary;
        this.matcher = matcher;
    }

    @Override
    public boolean equals(Object o)
    {
        if ( o instanceof Breakpoint) {
            return this.address == ((Breakpoint) o).address &&
                   this.isTemporary == ((Breakpoint) o).isTemporary &&
                   this.matcher.getExpression().equals( ((Breakpoint) o).matcher.getExpression() );
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( address, isTemporary, matcher.getExpression() );
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
