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
        switch ( childCount() )
        {
            case 0:
                throw new IllegalStateException( "Macro declaration needs to have at least one child node" );
            case 1:  // only child node is the macro identifier
                return null;
            case 2:
                return child( 1 ) instanceof MacroParameterList ? null : child( 1 );
            case 3:
                return child( 2 );
            default:
                throw new IllegalStateException("Macro declaration "+this+" has more than 3 child nodes?");
        }
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