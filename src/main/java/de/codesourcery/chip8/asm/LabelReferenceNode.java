package de.codesourcery.chip8.asm;

import org.apache.commons.lang3.Validate;

public class LabelReferenceNode extends ASTNode
{
    public final Identifier identifier;

    public LabelReferenceNode(Identifier identifier)
    {
        Validate.notNull( identifier, "identifier must not be null" );
        this.identifier = identifier;
    }

    @Override
    public String toString()
    {
        return "LabelReferenceNode[ "+identifier+" ]";
    }
}
