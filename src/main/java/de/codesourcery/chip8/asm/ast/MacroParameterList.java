package de.codesourcery.chip8.asm.ast;

public class MacroParameterList extends ASTNode
{
    @Override
    public ASTNode copyThisNode()
    {
        return new MacroParameterList();
    }
}
