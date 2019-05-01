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
