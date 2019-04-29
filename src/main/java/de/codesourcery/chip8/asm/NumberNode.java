package de.codesourcery.chip8.asm;

public class NumberNode extends ASTNode
{
    public final int value;

    public NumberNode(int number) {
        this.value = number;
    }

    @Override
    public String toString()
    {
        return "NumberNode[ "+value+" ]";
    }
}
