package de.codesourcery.chip8.emulator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Breakpoints
{
    public static final int SLOTS = 128;

    private final Map<Integer,Breakpoint>[] nonTemporary =
            new HashMap[ SLOTS ];

    private final Map<Integer,Breakpoint>[] temporary=
            new HashMap[ SLOTS ];

    private int size;

    public Breakpoints(int memSize) {

        for ( int i = 0 ; i < SLOTS ; i++ )
        {
            nonTemporary[i] = new HashMap<>(3);
            temporary[i] = new HashMap<>(3);
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isNotEmpty() {
        return size != 0;
    }

    public void add(Breakpoint bp) {

        final Breakpoint existing = getMap( bp.address, bp.isTemporary )
                .put( bp.address, bp );
        if ( existing == null ) {
            size++;
        }
    }

    public void remove(Breakpoint bp)
    {
        final Map<Integer, Breakpoint> map = getMap( bp.address, bp.isTemporary );
        if ( map.remove( bp.address ) != null ) {
            size--;
        }
    }

    private Map<Integer,Breakpoint> getMap(int address, boolean isTemporary)
    {
        final int slotNo = address & SLOTS;
        if ( isTemporary ) {
            return temporary[slotNo];
        }
        return nonTemporary[slotNo];
    }

    public int getBreakpoints(int address, Breakpoint[] destination)
    {
        final int slotNo = address % SLOTS;
        int index = 0;
        Integer key = null;
        Map<Integer, Breakpoint> map = nonTemporary[slotNo];
        if ( ! map.isEmpty() )
        {
            key = address;
            final Breakpoint bp = map.get( key );
            if ( bp != null ) {
                destination[index++] = bp;
            }
        }
        map = temporary[slotNo];
        if ( ! map.isEmpty() )
        {
            if ( key == null )
            {
                key = address;
            }
            final Breakpoint bp = map.get( key );
            if ( bp != null ) {
                destination[index++] = bp;
            }
        }
        return index;
    }

    public void clear()
    {
        Stream.of( temporary ).forEach( map -> map.clear() );
        Stream.of( nonTemporary ).forEach( map -> map.clear() );
        size = 0;
    }

    public void getAll(List<Breakpoint> destination)
    {
        Stream.of( temporary ).forEach( map -> destination.addAll( map.values() ) );
        Stream.of( nonTemporary ).forEach( map -> destination.addAll( map.values() ) );
    }
}