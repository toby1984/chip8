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

    public Identifier name() {
        return ((IdentifierNode) child(0)).identifier;
    }

    public MacroParameterList getParameterList()
    {
        return hasParameters() ? (MacroParameterList) child(1) : null;
    }

    public boolean hasParameters() {
        return childCount() > 1 && child(1) instanceof MacroParameterList;
    }

    public int parameterCount()
    {
        return hasParameters() ? getParameterList().childCount() : 0;
    }
}