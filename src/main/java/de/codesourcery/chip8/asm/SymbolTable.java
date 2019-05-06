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

import org.apache.commons.lang3.Validate;

import java.util.HashMap;
import java.util.Map;

/**
 * Crude symbol table.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SymbolTable
{
    private final Map<Identifier,Symbol> symbols = new HashMap<>();

    /**
     * A symbol.
     * @author tobias.gierke@code-sourcery.de
     */
    public static final class Symbol
    {
        public enum Type {
            LABEL,
            EQU,
            REGISTER_ALIAS
        }

        public final Identifier name;
        public Type type;
        public Object value;

        public Symbol(Identifier name)
        {
            this.name = name;
        }

        public Symbol(Identifier name,Symbol.Type type,Object value)
        {
            this.name = name;
            this.type = type;
            this.value = value;
        }

        public boolean isDefined()
        {
            return type != null && value != null;
        }
    }

    /**
     * Clears this symbol table.
     */
    public void clear() {
        this.symbols.clear();
    }

    /**
     * Declares a symbol.
     * @param identifier symbol name
     */
    public void declare(Identifier identifier) {
        declare(identifier,null);
    }

    /**
     * Declares a symbol.
     * @param identifier symbol name
     * @param type symbol type (may be NULL)
     */
    public void declare(Identifier identifier,Symbol.Type type)
    {
        Validate.notNull(identifier, "identifier must not be null");
        if ( ! symbols.containsKey( identifier ) ) {
            symbols.put( identifier, new Symbol(identifier,type,null) );
        }
    }

    /**
     * Defines a symbol.
     *
     * @param identifier symbol name
     * @param value symbol value (must not be NULL).
     */
    public void define(Identifier identifier, Symbol.Type type, Object value)
    {
        Validate.notNull(identifier, "identifier must not be null");
        Validate.notNull( type, "type must not be null" );
        Validate.notNull( value, "value must not be null" );

        Symbol existing = get( identifier );
        if ( existing == null ) {
            existing = new Symbol(identifier,type,value);
            symbols.put( identifier, existing );
            return;
        }
        if ( existing.value != null ) {
            throw new IllegalStateException( "Symbol "+existing+" is already declared" );
        }
        if ( existing.type != null && existing.type != type ) {
            throw new IllegalArgumentException("Refusing to redefine symbol "+existing+" with different type "+type);
        }
        existing.type = type;
        existing.value = value;
    }

    public void redefine(Identifier identifier, Symbol.Type type, Object value)
    {
        Validate.notNull(identifier, "identifier must not be null");
        Validate.notNull( type, "type must not be null" );
        Validate.notNull( value, "value must not be null" );

        Symbol existing = get( identifier );
        if ( existing == null ) {
            existing = new Symbol(identifier,type,value);
            symbols.put( identifier, existing );
            return;
        }
        if ( existing.type != null && existing.type != type ) {
            throw new IllegalArgumentException("Refusing to redefine symbol "+existing+" with different type "+type);
        }
        existing.type = type;
        existing.value = value;
    }

    /**
     * Get symbol by name.
     *
     * @param identifier
     * @return symbol or <code>null</code> if not found
     */
    public Symbol get(Identifier identifier) {
        return symbols.get( identifier );
    }

    /**
     * Returns whether a given symbol has been declared or defined.
     *
     * @param identifier
     * @return
     */
    public boolean isDeclared(Identifier identifier) {
        return get(identifier) != null;
    }
}
