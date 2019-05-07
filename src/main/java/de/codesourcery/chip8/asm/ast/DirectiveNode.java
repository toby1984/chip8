package de.codesourcery.chip8.asm.ast;

public class DirectiveNode extends ASTNode
{
    public enum Type
    {
        BYTE,
        WORD,
        RESERVE,
        EQU,
        ORIGIN,
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

    public static final boolean isValidDirective(String s)
    {
        if ( s != null )
        {
            switch (s.toLowerCase())
            {
                case ".equ":
                case ".alias":
                case ".origin":
                case ".byte":
                case ".word":
                case ".reserve":
                    return true;
            }
        }
        return false;
    }
}
