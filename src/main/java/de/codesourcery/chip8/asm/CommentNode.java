package de.codesourcery.chip8.asm;

public class CommentNode extends ASTNode
{
    public final String value;

    public CommentNode(String value)
    {
        this.value = value;
    }

    @Override
    public String toString()
    {
        return "CommentNode[ "+value+" ]";
    }
}
