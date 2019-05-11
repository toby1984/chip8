package de.codesourcery.chip8.asm.ast;

import de.codesourcery.chip8.asm.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MacroInvocationNode extends ASTNode
{
    public MacroInvocationNode()
    {
    }

    @Override
    public ASTNode copyThisNode()
    {
        return new MacroInvocationNode();
    }

    public Identifier getMacroName() {
        return ((IdentifierNode) child(0)).identifier;
    }

    public List<ASTNode> getArguments()
    {
        final List<ASTNode> result = new ArrayList<>();
        if ( childCount() > 1 ) {
            result.addAll( children.subList( 1, children.size() ) );
        }
        return result;
    }

    @Override
    public String toString()
    {
        return "MacroInvocation";
    }
}
