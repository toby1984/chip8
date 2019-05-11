package de.codesourcery.chip8.asm.ast;

public class ExpressionNode extends ASTNode
{
    public ExpressionNode(TextRegion region) {
        super(region);
    }

    @Override
    public ASTNode copyThisNode()
    {
        return new ExpressionNode(getRegionCopy());
    }

    @Override
    public String toString()
    {
        return "Expression";
    }
}
