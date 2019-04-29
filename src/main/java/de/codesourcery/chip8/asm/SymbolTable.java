package de.codesourcery.chip8.asm;

import org.apache.commons.lang3.Validate;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable
{
    private final Map<Identifier,Symbol> symbols = new HashMap<>();

    public static final class Symbol
    {
        public final Identifier name;
        public Object value;

        public Symbol(Identifier name)
        {
            this.name = name;
        }

        public Symbol(Identifier name,Object value)
        {
            this.name = name;
            this.value = value;
        }

        public boolean isDefined() {
            return value != null;
        }
    }

    public void clear() {
        this.symbols.clear();
    }

    public void add(Identifier identifier)
    {
        if ( ! symbols.containsKey( identifier ) ) {
            symbols.put( identifier, new Symbol(identifier ) );
        }
    }

    public void add(Identifier identifier,Object value)
    {
        Validate.notNull( value, "value must not be null" );

        Symbol existing = get( identifier );
        if ( existing == null ) {
            existing = new Symbol(identifier,value);
            symbols.put( identifier, existing );
            return;
        }
        if ( existing.value != null ) {
            throw new IllegalStateException( "Symbol "+existing+" is already declared" );
        }
        existing.value = value;
    }

    public Symbol get(Identifier identifier) {
        return symbols.get( identifier );
    }
}
