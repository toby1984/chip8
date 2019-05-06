package de.codesourcery.chip8.asm.ast;

public class DirectiveNode extends ASTNode
{
    public enum Type
    {
        INITIALIZED_MEMORY,
        UNINITIALIZED_MEMORY,
        EQU,
        ALIAS
    }

    public final Type type;

    public DirectiveNode(Type type, TextRegion region)
    {
        super(region);
        this.type = type;
    }

    @Override
    public String toString()
    {
        return "Directive [ "+type+" ]";
    }
}
