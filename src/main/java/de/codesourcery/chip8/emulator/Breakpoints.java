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

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.List;

/**
 * Performance-optimized collection of temporary and permanent {@link Breakpoints}.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Breakpoints
{
    private final Int2ObjectOpenHashMap<Breakpoint> permanent = new Int2ObjectOpenHashMap<>(20);
    private final Int2ObjectOpenHashMap<Breakpoint> temporary = new Int2ObjectOpenHashMap<>(20);

    private final String name;
    private int size;

    public Breakpoints(String name) {
        this.name = name;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean isNotEmpty() {
        return ! isEmpty();
    }

    public void add(Breakpoint bp) {

        System.out.println("About top add breakpoint "+bp+" to "+this);
        final Breakpoint existing = getMap( bp.address, bp.isTemporary )
                .put( bp.address, bp );
        if ( existing == null ) {
            size++;
        }
        System.out.println("Added breakpoint "+bp+" to "+this);
    }

    public void remove(Breakpoint bp)
    {
        final Int2ObjectOpenHashMap<Breakpoint> map = getMap( bp.address, bp.isTemporary );
        if ( map.remove( bp.address ) != null ) {
            size--;
        }
    }

    private Int2ObjectOpenHashMap<Breakpoint> getMap(int address, boolean isTemporary)
    {
        return isTemporary ? temporary : permanent;
    }

    public boolean checkBreakpointHit(int address)
    {
        if ( size == 0 ) {
            return false;
        }
        Breakpoint hit = temporary.get(address);
        if ( hit != null )
        {
            remove(hit);
            return true;
        }
        return permanent.containsKey(address);
    }

    public void clear()
    {
        temporary.clear();
        permanent.clear();
        size = 0;
    }

    public void getAll(List<Breakpoint> destination)
    {
        destination.addAll( temporary.values() );
        destination.addAll( permanent.values() );
    }

    public boolean contains(Breakpoint bp)
    {
        if ( bp.isTemporary ) {
            return temporary.containsKey(bp.address);
        }
        return permanent.containsKey(bp.address);
    }

    public void clearTemporary()
    {
        final int tmpCount = temporary.size();
        if ( size - tmpCount < 0 ) {
            throw new IllegalStateException("Internal error, negative size ?");
        }
        size -= tmpCount;
        temporary.clear();
    }

    @Override
    public String toString()
    {
        return "Breakpoints[ "+name+" ]";
    }
}