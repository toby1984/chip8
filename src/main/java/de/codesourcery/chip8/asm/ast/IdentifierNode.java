package de.codesourcery.chip8.asm.ast;

import de.codesourcery.chip8.asm.Identifier;
import org.apache.commons.lang3.Validate;

public class IdentifierNode extends ASTNode
{
    public final Identifier identifier;

    public IdentifierNode(Identifier identifier, TextRegion region)
    {
        super(region);
        Validate.notNull( identifier, "identifier must not be null" );
        this.identifier = identifier;
    }

    @Override
    public String toString()
    {
        return "Identifier[ "+identifier+" ]";
    }
}
