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

    public boolean checkBreakpointHit(int address)
    {
        if ( size == 0 ) {
            return false;
        }
        final int slotNo = address % SLOTS;
        Map<Integer, Breakpoint> map = temporary[slotNo];
        Integer key = null;
        if ( ! map.isEmpty() )
        {
            key = address;
            if ( map.containsKey( key ) )
            {
                map.remove( key );
                return true;
            }
        }
        map = nonTemporary[slotNo];
        if ( ! map.isEmpty() )
        {
            if ( key == null ) {
                key = address;
            }
            return map.containsKey( key );
        }
        return false;
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