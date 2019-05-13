package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.ExpressionNode;
import de.codesourcery.chip8.asm.ast.IdentifierNode;
import de.codesourcery.chip8.asm.ast.NumberNode;
import de.codesourcery.chip8.asm.ast.OperatorNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsible for evaluating a syntax tree / subtree into a number that can be included in the generated executable/instructions.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ExpressionEvaluator
{
    @FunctionalInterface
    public interface INodeEvaluator
    {
        Object evaluate(ASTNode node,boolean failOnErrors);
    }

    public static class NodeEvaluator implements INodeEvaluator {

        private final ISymbolResolver resolver;

        public NodeEvaluator(ISymbolResolver resolver)
        {
            this.resolver = resolver;
        }

        public Object evaluate(ASTNode node,boolean failOnErrors)
        {
            if ( node instanceof NumberNode) {
                return ((NumberNode) node).value;
            }
            if ( node instanceof OperatorNode )
            {
                final List<Object> values = new ArrayList<>();
                for ( ASTNode child : node.children )
                {
                    values.add( evaluate( child, failOnErrors) );
                }
                return ((OperatorNode) node).operator.evaluate( values );
            }
            if ( node instanceof ExpressionNode)
            {
                if ( node.childCount() == 0 ) {
                    throw new RuntimeException("Expression "+node+" has no children ?");
                }
                return evaluate( node.child(0), failOnErrors );
            }
            if ( node instanceof IdentifierNode )
            {
                return evaluateIdentifier( (IdentifierNode) node, failOnErrors );
            }
            throw new RuntimeException("Internal error, don't know how to evaluate "+node);
        }

        protected Object evaluateIdentifier(IdentifierNode node,boolean failOnErrors)
        {
            final Identifier id = node.identifier;
            final SymbolTable.Symbol symbol = resolver.get( id );
            if ( symbol != null )
            {
                if ( symbol.value != null ) {
                    return symbol.value;
                }
                if ( failOnErrors ) {
                    throw new RuntimeException("Symbol '"+id.value+"' @ "+node+" is declared but not defined (=no value available yet)");
                }
            }
            else if ( failOnErrors )
            {
                throw new RuntimeException( "Unknown symbol '" + id.value + "' @ " + node );
            }
            return null;
        }
    }

    public static Integer evaluateAddress(ASTNode node,INodeEvaluator context, boolean failOnErrors)
    {
        final Integer value = evaluateNumber( node, context, failOnErrors );
        if ( value != null && (value < 0 || value > 0xfff) )
        {
            throw new RuntimeException( "Expected a 12-bit value but got " + value + " @ " + node );
        }
        return value;
    }

    public static Integer evaluateWord(ASTNode node,INodeEvaluator context, boolean failOnErrors)
    {
        final Integer value = evaluateNumber( node, context, failOnErrors );
        if ( value != null && (value < 0 || value > 65535) )
        {
            throw new RuntimeException( "Expected a 16-bit value but got " + value + " @ " + node );
        }
        return value;
    }

    public static Integer evaluateByte(ASTNode node,INodeEvaluator context, boolean failOnErrors)
    {
        final Integer value = evaluateNumber( node, context, failOnErrors );
        if ( value != null && (value < 0 || value > 255) )
        {
            throw new RuntimeException( "Expected an 8-bit value but got " + value + " @ " + node );
        }
        return value;
    }

    public static Integer evaluateNumber(ASTNode node,INodeEvaluator context, boolean failOnErrors)
    {
        Object value = context.evaluate(node, failOnErrors);
        if ( value != null ) {
            if ( !(value instanceof Number) ) {
                throw new RuntimeException("Expected a number but got "+value+" @ "+node);
            }
            return ((Number) value).intValue();
        }
        return null;
    }

}