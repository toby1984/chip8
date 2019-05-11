/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.chip8.asm.ast;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Superclass for AST nodes.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class ASTNode
{
    /**
     * AST node visitor.
     */
    @FunctionalInterface
    public interface Visitor
    {
        /**
         * Visit an AST node.
         *
         * @param node node to visit
         * @param depth Depth of this node, relative to iteration start.
         */
        void visit(ASTNode node,int depth);
    }

    public final List<ASTNode> children = new ArrayList<>();

    public ASTNode parent;

    private TextRegion region;

    public ASTNode() {
    }

    /**
     * Create instance with a specific region.
     * @param region
     */
    public ASTNode(TextRegion region) {
        this.region = region;
    }

    /**
     * Returns the source region covered by this specific AST node.
     *
     * @return this node's region (may be NULL)
     */
    public final TextRegion getRegion()
    {
        return region;
    }

    /**
     * Returns a copy of this node's region (if any).
     *
     * @return copy of this node's region or <code>null</code>
     * @see #getRegion()
     */
    public final TextRegion getRegionCopy() {
        return region == null ? null : region.createCopy();
    }

    /**
     * Returns the source region covered by this specific AST node and all of its child nodes (if any).
     * @return
     */
    public final TextRegion getCombinedRegion()
    {
        TextRegion result = region == null ? null : region.createCopy();
        for ( ASTNode child : children )
        {
            if ( result == null )
            {
                result = child.getCombinedRegion();
            } else {
                result.merge(child.getCombinedRegion() );
            }
        }
        return result;
    }

    /**
     * Add a child node.
     *
     * @param node
     */
    public final void add(ASTNode node)
    {
        node.parent = this;
        this.children.add( node );
    }

    /**
     * Add multiple children.
     *
     * @param toAdd
     */
    public final void add(List<ASTNode> toAdd)
    {
        toAdd.forEach( this::add );
    }

    /**
     * Returns the number of child nodes.
     *
     * @return
     */
    public final int childCount() {
        return children.size();
    }

    /**
     * Returns the child at a given index.
     *
     * @param idx
     * @return
     */
    public final ASTNode child(int idx) {
        return children.get(idx);
    }

    @Override
    public String toString()
    {
        return "ASTNode";
    }

    /**
     * Traverse the subtree starting at this node in-order.
     * @param visitor
     */
    public final void visitInOrder(Visitor visitor) {
        visitInOrder(visitor,0);
    }

    private final void visitInOrder(Visitor visitor, int depth)
    {
        visitor.visit( this, depth );
        children.forEach( c -> c.visitInOrder( visitor, depth+1 ) );
    }

    public final String toPrettyString() {
        final StringBuilder buffer = new StringBuilder();
        visitInOrder( (node,depth) -> {
           buffer.
                   append( StringUtils.repeat( "  ", depth ) )
                   .append( " - " ).append( node.toString() );
           buffer.append("\n");
        });
        return buffer.toString();
    }

    public final boolean hasNoChildren() {
        return children.isEmpty();
    }

    public final boolean hasChildren() {
        return ! children.isEmpty();
    }

    public final ASTNode createCopy() {
        final ASTNode result = copyThisNode();
        for ( ASTNode child : children ) {
            result.add( child.createCopy() );
        }
        return result;
    }

    public void replaceWith(ASTNode other) {
        if ( parent == null ) {
            throw new IllegalStateException( "Can't invoke replaceWith() on a node with no parent" );
        }
        final int idx = parent.children.indexOf(this);
        if ( idx == -1 ) {
            throw new IllegalStateException( "Failed to find myself in parent node?" );
        }
        parent.children.set(idx,other);
        other.parent = parent;
    }

    public abstract ASTNode copyThisNode();
}
