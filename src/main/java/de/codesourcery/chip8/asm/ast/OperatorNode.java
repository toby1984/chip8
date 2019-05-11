package de.codesourcery.chip8.asm.ast;

import de.codesourcery.chip8.asm.Operator;
import org.apache.commons.lang3.Validate;

public class OperatorNode extends ASTNode
{
    public final Operator operator;

    public OperatorNode(Operator op, TextRegion region)
    {
        super(region);
        Validate.notNull( op, "op must not be null" );
        this.operator = op;
    }

    @Override
    public ASTNode copyThisNode()
    {
        return new OperatorNode(this.operator, getRegionCopy());
    }

    @Override
    public String toString()
    {
        return "OperatorNode[ "+operator+" ]";
    }
}
