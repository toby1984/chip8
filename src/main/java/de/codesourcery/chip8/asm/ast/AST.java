package de.codesourcery.chip8.asm.ast;

public class AST extends ASTNode
{
    @Override
    public ASTNode copyThisNode()
    {
        return new AST();
    }

    @Override
    public String toString()
    {
        return "AST";
    }
}
