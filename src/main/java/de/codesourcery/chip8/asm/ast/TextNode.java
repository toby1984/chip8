package de.codesourcery.chip8.asm.ast;

import de.codesourcery.chip8.asm.Identifier;

public class TextNode extends ASTNode
{
    public final String value;

    public TextNode(String value)
    {
        this.value = value;
    }

    public boolean isValidIdentifier() {
        return Identifier.isValid(value);
    }

    @Override
    public String toString()
    {
        return "TextNode[ "+value+" ]";
    }
}
