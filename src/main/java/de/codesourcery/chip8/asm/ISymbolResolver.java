package de.codesourcery.chip8.asm;

/**
 * Implementations know how to resolve a symbol.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface ISymbolResolver
{
    /**
     * Try to resolve a symbol.
     *
     * @param scope Symbol scope
     * @param name Symbol name
     * @return symbol or <code>null</code>
     */
    SymbolTable.Symbol get(Identifier scope, Identifier name);

    /**
     * Try to resolve a symbol from the current scope.
     *
     * The actual semantics (=what is the current scope) are implementation-dependent.
     *
     * @param name
     * @return
     */
    SymbolTable.Symbol get(Identifier name);
}