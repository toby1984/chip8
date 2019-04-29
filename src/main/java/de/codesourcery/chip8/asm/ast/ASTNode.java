package de.codesourcery.chip8.asm.ast;

import java.util.ArrayList;
import java.util.List;

public class ASTNode
{
    public final List<ASTNode> children = new ArrayList<>();
    public ASTNode parent;

    public void add(ASTNode node)
    {
        node.parent = this;
        this.children.add( node );
    }

    public int childCount() {
        return children.size();
    }

    public ASTNode child(int idx) {
        return children.get(idx);
    }

    @Override
    public String toString()
    {
        return "ASTNode";
    }

    public interface Visitor {
        public void visit(ASTNode node,int depth);
    }

    public void visitInOrder(Visitor visitor) {
        visitInOrder(visitor,0);
    }

    public void visitInOrder(Visitor visitor,int depth)
    {
        visitor.visit( this, depth );
        children.forEach( c -> c.visitInOrder( visitor, depth+1 ) );
    }
}
