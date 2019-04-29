package de.codesourcery.chip8.asm.ast;

import de.codesourcery.chip8.asm.Identifier;

public class LabelNode extends ASTNode
{
    public Identifier id;

    public LabelNode(Identifier id)
    {
        this.id = id;
    }

    @Override
    public String toString()
    {
        return "LabelNode[ "+id+" ]";
    }
}
