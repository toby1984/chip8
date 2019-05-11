package de.codesourcery.chip8.asm.ast;

public class DirectiveNode extends ASTNode
{
    public enum Type
    {
        BYTE( "byte" ),
        WORD( "word" ),
        RESERVE( "reserve" ),
        EQU( "equ" ),
        ORIGIN( "origin" ),
        ALIAS( "alias" ),
        MACRO( "macro" ),
        CLEAR_ALIASES( "clearAliases" );

        public final String keyword;

        Type(String keyword)
        {
            this.keyword = keyword;
        }
    }

    public final Type type;

    public DirectiveNode(Type type, TextRegion region)
    {
        super(region);
        this.type = type;
    }

    @Override
    public ASTNode copyThisNode()
    {
        return new DirectiveNode(this.type,getRegionCopy());
    }

    @Override
    public String toString()
    {
        return "Directive [ "+type+" ]";
    }
}
