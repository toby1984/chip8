package de.codesourcery.chip8.asm.ast;

import de.codesourcery.chip8.asm.Identifier;

/**
 * Child nodes:
 *
 * Child no. - Description
 * 0 - Macro name (IdentifierNode), mandatory
 * 1 - Parameter (MacroParameterList), optional
 * 1/2 - Macro body AST
 */
public class MacroDeclarationNode extends DirectiveNode
{
    public MacroDeclarationNode(TextRegion region)
    {
        super( Type.MACRO, region );
    }

    @Override
    public ASTNode copyThisNode()
    {
        return new MacroDeclarationNode(getRegionCopy());
    }

    public Identifier name() {
        return ((IdentifierNode) child(0)).identifier;
    }

    public MacroParameterList getParameterList()
    {
        return hasParameters() ? (MacroParameterList) child(1) : new MacroParameterList();
    }

    public boolean hasParameters() {
        return childCount() > 1 && child(1) instanceof MacroParameterList;
    }

    public ASTNode getMacroBody()
    {
        if ( childCount() == 0 ) {
            return null;
        }
        if ( childCount() == 1 ) { // only macro identifier
            return child( 0 ) instanceof MacroParameterList ? null : child( 0 );
        }
        if ( childCount() == 2 ) {
            return child(1);
        }
        throw new IllegalStateException("Macro declaration "+this+" has more than 2 child nodes?");
    }

    public int parameterCount()
    {
        return hasParameters() ? getParameterList().childCount() : 0;
    }

    @Override
    public String toString()
    {
        return "MacroDeclaration";
    }
}