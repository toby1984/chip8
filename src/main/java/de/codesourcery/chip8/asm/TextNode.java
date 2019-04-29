package de.codesourcery.chip8.asm;

public class TextNode extends ASTNode
{
    public final String value;

    public TextNode(String value)
    {
        this.value = value;
    }

    @Override
    public String toString()
    {
        return "TextNode[ "+value+" ]";
    }
}
