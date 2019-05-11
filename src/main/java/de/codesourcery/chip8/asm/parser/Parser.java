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
package de.codesourcery.chip8.asm.parser;

import de.codesourcery.chip8.asm.Assembler;
import de.codesourcery.chip8.asm.ExecutableWriter;
import de.codesourcery.chip8.asm.ExpressionEvaluator;
import de.codesourcery.chip8.asm.Identifier;
import de.codesourcery.chip8.asm.Operator;
import de.codesourcery.chip8.asm.SymbolTable;
import de.codesourcery.chip8.asm.ast.AST;
import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.CommentNode;
import de.codesourcery.chip8.asm.ast.DirectiveNode;
import de.codesourcery.chip8.asm.ast.ExpressionNode;
import de.codesourcery.chip8.asm.ast.IdentifierNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.ast.MacroDeclarationNode;
import de.codesourcery.chip8.asm.ast.MacroInvocationNode;
import de.codesourcery.chip8.asm.ast.MacroParameterList;
import de.codesourcery.chip8.asm.ast.NumberNode;
import de.codesourcery.chip8.asm.ast.OperatorNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.ast.StatementNode;
import de.codesourcery.chip8.asm.ast.TextNode;
import de.codesourcery.chip8.asm.ast.TextRegion;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parser.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Parser
{
    private final Lexer lexer;
    private final Assembler.CompilationContext context;

    protected static final class ParseException extends RuntimeException {

        public ParseException(String message)
        {
            super( message );
        }
    }

    public Parser(Lexer lexer, Assembler.CompilationContext context)
    {
        this.lexer = lexer;
        this.context = context;
    }

    public ASTNode parse() {

        try {
            return internalParse();
        }
        catch(RuntimeException e)
        {
            printError( e.getMessage(), lexer.peek().offset );
            throw e;
        }
    }

    private void error(String message)
    {
        context.error(message, lexer.peek().offset );
        throw new ParseException(message);
    }

    private void error(String message,Token token)
    {
        context.error(message, token );
        throw new ParseException(message);
    }

    private void error(String message,ASTNode node)
    {
        context.error(message, node);
        throw new ParseException(message);
    }

    private ASTNode internalParse() {

        final ASTNode ast = new AST();
        while ( ! lexer.eof() )
        {
            skipNewlines();
            if ( ! lexer.eof() )
            {
                final ASTNode stmt;
                try
                {
                    stmt = parseStatement();
                    if (stmt == null)
                    {
                        error("No statement on this line?");
                    }
                    ast.add(stmt);
                }
                catch(Exception e)
                {
                    if ( !(e instanceof ParseException) ) {
                        context.error("Internal error - "+e.getMessage(),lexer.peek().offset);
                    }
                    printError( e.getMessage(), lexer.peek().offset );
                    // crude error recovery - just advance to next line and hope for the best
                    while (!lexer.eof() && ! lexer.peek().is(TokenType.NEWLINE)) {
                        lexer.next();
                    }
                }
            }
        }
        return ast;
    }

    private ASTNode parseStatement()
    {
        final ASTNode statement = new StatementNode();
        final ASTNode label = parseLabel();
        if ( label != null ) {
            statement.add( label );
        }
        final ASTNode insn = parseInstruction();
        if ( insn != null ) {
            statement.add( insn );
        }
        final ASTNode comment = parseComment();
        if ( comment != null ) {
            statement.add( comment );
        }
        if ( statement.children.isEmpty() )
        {
            error("Syntax error (not valid statement)");
        }
        return statement;
    }

    public ASTNode parseExpression()
    {
        return parseBitwiseOr();
    }

    private ASTNode parseBitwiseOr()
    {
        final ASTNode operand0 = parseBitwiseXor();
        if ( operand0 != null )
        {
            Token tok = lexer.peek();
            if ( tok.is(TokenType.OPERATOR) )
            {
                final Operator op = Operator.parseOperator( tok.value );
                if ( op == Operator.BITWISE_OR )
                {
                    tok = lexer.next();
                    ASTNode operand1 = parseBitwiseXor();
                    if ( operand1 == null ) {
                        error("Expected an argument");
                    }
                    final OperatorNode opNode = new OperatorNode(op, tok.region() );
                    opNode.add( operand0 );
                    opNode.add( operand1 );
                    return opNode;
                }
            }
        }
        return operand0;
    }

    private ASTNode parseBitwiseXor()
    {
        final ASTNode operand0 = parseBitwiseAnd();
        if ( operand0 != null )
        {
            Token tok = lexer.peek();
            if ( tok.is(TokenType.OPERATOR) )
            {
                final Operator op = Operator.parseOperator( tok.value );
                if ( op == Operator.BITWISE_XOR )
                {
                    tok = lexer.next();
                    ASTNode operand1 = parseBitwiseAnd();
                    if ( operand1 == null ) {
                        error("Expected an argument");
                    }
                    final OperatorNode opNode = new OperatorNode(op, tok.region() );
                    opNode.add( operand0 );
                    opNode.add( operand1 );
                    return opNode;
                }
            }
        }
        return operand0;
    }

    private ASTNode parseBitwiseAnd()
    {
        final ASTNode operand0 = parseBitshift();
        if ( operand0 != null )
        {
            Token tok = lexer.peek();
            if ( tok.is(TokenType.OPERATOR) )
            {
                final Operator op = Operator.parseOperator( tok.value );
                if ( op == Operator.BITWISE_AND )
                {
                    tok = lexer.next();
                    ASTNode operand1 = parseBitshift();
                    if ( operand1 == null ) {
                        error("Expected an argument");
                    }
                    final OperatorNode opNode = new OperatorNode(op, tok.region() );
                    opNode.add( operand0 );
                    opNode.add( operand1 );
                    return opNode;
                }
            }
        }
        return operand0;
    }

    private ASTNode parseBitshift()
    {
        final ASTNode operand0 = parseAddition();
        if ( operand0 != null )
        {
            Token tok = lexer.peek();
            if ( tok.is(TokenType.OPERATOR) )
            {
                final Operator op = Operator.parseOperator( tok.value );
                if ( op == Operator.SHIFT_LEFT|| op == Operator.SHIFT_RIGHT )
                {
                    tok = lexer.next();
                    ASTNode operand1 = parseAddition();
                    if ( operand1 == null ) {
                        error("Expected an argument");
                    }
                    final OperatorNode opNode = new OperatorNode(op, tok.region() );
                    opNode.add( operand0 );
                    opNode.add( operand1 );
                    return opNode;
                }
            }
        }
        return operand0;
    }

    private ASTNode parseAddition()
    {
        final ASTNode operand0 = parseMultiplication();
        if ( operand0 != null )
        {
            Token tok = lexer.peek();
            if ( tok.is(TokenType.OPERATOR) )
            {
                final Operator op = Operator.parseOperator( tok.value );
                if ( op == Operator.PLUS || op == Operator.MINUS )
                {
                    tok = lexer.next();
                    ASTNode operand1 = parseMultiplication();
                    if ( operand1 == null ) {
                        error("Expected an argument");
                    }
                    final OperatorNode opNode = new OperatorNode(op, tok.region() );
                    opNode.add( operand0 );
                    opNode.add( operand1 );
                    return opNode;
                }
            }
        }
        return operand0;
    }

    /*
expression     → addition;
addition       → multiplication ( ( "-" | "+" ) multiplication )* ;
multiplication → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "-" ) unary | operand ;
operand        → NUMBER | STRING | "false" | "true" | "nil"
               | "(" expression ")" ;
     */
    private ASTNode parseMultiplication()
    {
        final ASTNode operand0 = parseBitwiseNegation();
        if ( operand0 != null )
        {
            Token tok = lexer.peek();
            if ( tok.is(TokenType.OPERATOR) )
            {
                final Operator op = Operator.parseOperator( tok.value );
                if ( op == Operator.MULTIPLY || op == Operator.DIVIDE )
                {
                    tok = lexer.next();
                    ASTNode operand1 = parseBitwiseNegation();
                    if ( operand1 == null ) {
                        error("Expected an argument");
                    }
                    final OperatorNode opNode = new OperatorNode(op, tok.region() );
                    opNode.add( operand0 );
                    opNode.add( operand1 );
                    return opNode;
                }
            }
        }
        return operand0;
    }

    private ASTNode parseUnary()
    {
        if ( lexer.peek().is(TokenType.OPERATOR) && "-".equals( lexer.peek().value ) )
        {
            final Token tok = lexer.next();
            OperatorNode op = new OperatorNode(Operator.UNARY_MINUS,tok.region());
            final ASTNode operand = parseUnary();
            if ( operand == null ) {
                error("Expected an argument");
            }
            op.add( operand );
            return op;
        }
        return parseOperand();
    }

    private ASTNode parseBitwiseNegation()
    {
        if ( lexer.peek().is(TokenType.OPERATOR) && "~".equals( lexer.peek().value ) )
        {
            final Token tok = lexer.next();
            OperatorNode op = new OperatorNode(Operator.BITWISE_NEGATION,tok.region());
            final ASTNode operand = parseBitwiseNegation();
            if ( operand == null ) {
                error("Expected an argument");
            }
            op.add( operand );
            return op;
        }
        return parseUnary();
    }

    private ASTNode parseOperand()
    {
        Token tok = lexer.peek();
        switch( tok.type )
        {
            case HEX_NUMBER:
            case DECIMAL_NUMBER:
            case BINARY_NUMBER:
                final int value;
                switch( tok.type)
                {
                    case HEX_NUMBER:
                        value = Integer.parseInt( lexer.next().value.substring(2), 16 );
                        break;
                    case DECIMAL_NUMBER:
                        value = Integer.parseInt( lexer.next().value );
                        break;
                    case BINARY_NUMBER:
                        value = Integer.parseInt( lexer.next().value.substring(1) ,2 );
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached: "+tok.type);
                }
                return new NumberNode( value, tok.region());
            case REGISTER:
                final int regNum = Integer.parseInt( lexer.next().value.substring( 1 ) );
                return new RegisterNode( regNum, tok.region() );
            case IDENTIFIER:
                return parseIdentifierOrMacroInvocation();
            case TEXT:
                return new TextNode( lexer.next().value, tok.region() );
            default:
        }
        if ( lexer.peek().is(TokenType.PARENS_OPEN ) )
        {
            tok = lexer.next();
            final ASTNode result = parseExpression();
            if ( result == null ) {
                error( "Missing input after opening parentheses");
            }
            if ( ! lexer.peek().is(TokenType.PARENS_CLOSE ) ) {
                error( "Missing closing parentheses");
            }
            final TextRegion region = tok.region();
            region.merge( lexer.next().region() );
            final ExpressionNode expr = new ExpressionNode( region );
            expr.add( result );
            return expr;
        }
        return null;
    }

    private ASTNode parseLabel()
    {
        if ( lexer.peek().is(TokenType.DOT ) ) {
            final Token dot = lexer.next();
            if ( lexer.peek().is(TokenType.IDENTIFIER ) )
            {
                final String value = lexer.peek().value;
                if ( Identifier.isReserved( value ) )
                {
                    lexer.pushBack( dot );
                    return null;
                }
                // local label
                Token label = lexer.next();
                return new LabelNode( Identifier.of(label.value) , true, label.region() );
            }
            else
            {
                lexer.pushBack( dot );
            }
        }
        if ( lexer.peek().is(TokenType.IDENTIFIER ) )
        {
            Token tok = lexer.next();
            if ( lexer.peek().is(TokenType.COLON ) )
            {
                lexer.next();
                return new LabelNode( new Identifier( tok.value ) , false, tok.region() );
            }
            lexer.pushBack( tok );
        }
        return null;
    }

    private ASTNode parseInstruction()
    {
        ASTNode result = parseDirective();
        if ( result != null ) {
            return result;
        }

        final Token tok = lexer.peek();
        if ( isMacroInvocation( tok ) )
        {
            return parseIdentifierOrMacroInvocation();
        }
        if ( tok.is(TokenType.TEXT) )
        {
            Instruction match= Stream.of( Instruction.values() ).filter(  x->x.mnemonic.equalsIgnoreCase( tok.value) ).findFirst().orElse( null );
            if ( match != null )
            {
                lexer.next();
                final InstructionNode insn = new InstructionNode( match.mnemonic, tok.region() );
                ASTNode op = null;
                boolean required = false;
                while ( ( op = parseExpression() ) != null )
                {
                    required = false;
                    insn.add( op );
                    if ( lexer.peek().is(TokenType.COMMA ) ) {
                        lexer.next();
                        required = true;
                    }
                }
                if ( required ) {
                    error("Expected operand after comma");
                }
                return insn;
            }
        }
        return null;
    }

    private MacroDeclarationNode parseMacro(TextRegion region)
    {
        final Token tok = lexer.peek();
        if ( ! tok.is(TokenType.IDENTIFIER) )
        {
            error("Expected macro identifier");
            return null;
        }
        final Identifier id = Identifier.of( tok.value );
        if ( context.symbolTable.isDeclared( SymbolTable.GLOBAL_SCOPE, id ) )
        {
            error("Macro name '"+id.value+"' is clashing with already defined symbol "+
                    context.symbolTable.get(SymbolTable.GLOBAL_SCOPE, id));
            return null;
        }
        lexer.next(); // consume macro name

        final MacroDeclarationNode result =  new MacroDeclarationNode(region);
        result.add( new IdentifierNode( id, tok.region() ) );

        context.symbolTable.define( SymbolTable.GLOBAL_SCOPE,
                id, SymbolTable.Symbol.Type.MACRO,result);

        // parse parameter list (if any)
        if ( lexer.peek().is(TokenType.PARENS_OPEN ) )
        {
            lexer.next(); // consume '('

            final MacroParameterList paramList = new MacroParameterList();
            result.add( paramList );
            final List<ASTNode> list = parseExpressionList( false );
            list.forEach( x -> {
                if ( x instanceof IdentifierNode )
                {
                    paramList.add( x );
                }
                else
                {
                    error("Expect an identifier (macro parameter name)",x);
                }
            });

            if ( ! lexer.peek().is(TokenType.PARENS_CLOSE ) ) {
                error("Expected closing parens");
            }
            lexer.next(); // consume ')'
        }

        // parse macro body
        final StringBuilder buffer = new StringBuilder();
        ASTNode body;
        if ( "=".equals( lexer.peek().value ) ) {
            // expression macro
            lexer.next(); // consume '='

            skipNewlines();

            final int bodyStartOffset = lexer.peek().offset;
            body = parseExpression();
            if ( body == null ) {
                error("Expected an expression");
            }
        }
        else
        {
            // support both ".macro stuff {" and ".macro stuff <newline> {"
            skipNewlines();
            if ( ! lexer.peek().is(TokenType.CURLY_PARENS_OPEN ) ) {
                error("Expected start of macro body");
            }
            lexer.next(); // consume '{'

            skipNewlines(); // consume leading newlines so expression parsing doesn't fail

            lexer.setSkipWhitespace( false);
            try
            {
                final int bodyStartOffset = lexer.peek().offset;
                while ( !lexer.eof() && !lexer.peek().is( TokenType.CURLY_PARENS_CLOSE ) )
                {
                    buffer.append( lexer.next().value );
                }
                body = parseMacroBody( buffer.toString(), bodyStartOffset );
            }
            finally {
                lexer.setSkipWhitespace( true );
            }
            if ( lexer.peek().is(TokenType.CURLY_PARENS_CLOSE ) ) {
                lexer.next(); // consume '}'
            } else {
                error("Expected closing curl parentheses");
            }
        }
        if ( body != null )
        {
            result.add( body );
        }
        return result;
    }

    public static ASTNode expandMacroInvocation(MacroInvocationNode macroInvocation, Assembler.CompilationContext context)
    {
        // look up macro declaration
        final SymbolTable.Symbol symbol = context.symbolTable.get( SymbolTable.GLOBAL_SCOPE, macroInvocation.getMacroName() );
        if ( ! symbol.hasType( SymbolTable.Symbol.Type.MACRO ) ) {
            throw new IllegalStateException("Expected macro symbol '"+macroInvocation.getMacroName().value+"' but got "+symbol);
        }
        MacroDeclarationNode decl = (MacroDeclarationNode) symbol.value;
        if ( decl == null ) {
            throw new IllegalStateException("Missing declaration for macro symbol '"+macroInvocation.getMacroName().value+"'");
        }

        // create copy of macro body AST
        ASTNode bodyCopy = decl.getMacroBody();
        if ( bodyCopy == null )
        {
            context.error("No macro body for "+symbol,macroInvocation);
            return macroInvocation;
        }
        bodyCopy = bodyCopy.createCopy();

        // replace all labels with newly generated ones
        final Map<Identifier,Identifier> labelMappings = new HashMap<>();
        bodyCopy.visitInOrder( (n,depth) ->
        {
            if ( n instanceof LabelNode ) {
                final Identifier oldId = ((LabelNode) n).id;
                final Identifier newId = context.generateUniqueIdentifier();
                context.symbolTable.declare( SymbolTable.GLOBAL_SCOPE, newId, SymbolTable.Symbol.Type.LABEL );
                labelMappings.put( oldId, newId );
                ((LabelNode) n).id = newId;
            }
        });

        // rewrite all references to old labels to refer to the new ones instead
        bodyCopy.visitInOrder( (n,depth ) -> {
           if ( n instanceof IdentifierNode)
           {
               final Identifier id = ((IdentifierNode) n).identifier;
               final Identifier replacement = labelMappings.get( id );
               if ( replacement != null )
               {
                   ((IdentifierNode) n).identifier = replacement;
               }
           }
        });

        // check number of invocation arguments matches number of macro parameters
        final List<ASTNode> arguments = macroInvocation.getArguments();
        final MacroParameterList parameterList = decl.getParameterList();
        if ( arguments.size() != parameterList.childCount() ) {
            context.error("Wrong number of macro arguments, expected "+
                    decl.parameterCount()+" but found "+macroInvocation.getArguments().size(), macroInvocation);
            return macroInvocation;
        }
        // map parameter names to parameter indices
        final Map<Identifier,Integer> paramIndexByName = new HashMap<>();
        int index = 0;
        for ( ASTNode child : decl.getParameterList().children ) {
            if ( !(child instanceof IdentifierNode ) ) {
                context.error("Macro "+symbol.name.value+" has bad parameter list,expected an identifier",child);
            }
            final Identifier paramName = ((IdentifierNode) child).identifier;
            Integer existing = paramIndexByName.put( paramName, index++);
            if ( existing != null ) {
                context.error("Macro "+symbol.name.value+" has duplicate parameter name '"+paramName.value+"'",child);
            }
        }
        // replace parameter references with actual values
        bodyCopy.visitInOrder( (n,depth) ->
        {
                if ( n instanceof IdentifierNode)
                {
                    Integer idx = paramIndexByName.get( ((IdentifierNode) n).identifier );
                    if ( idx != null ) { // substitute with parameter
                        n.replaceWith( arguments.get( idx ).createCopy() );
                    }
                }
        });
        return bodyCopy;
    }

    private ASTNode parseMacroBody(String body, int bodyStartOffset) {

        final Assembler.CompilationContext tmpCtx = new Assembler.CompilationContext(new ExecutableWriter());
        final Parser p = new Parser(new Lexer(new Scanner(body)),tmpCtx);
        final ASTNode result = p.parse();

        // fix (error) message offsets
        tmpCtx.messages.stream()
                .map( x -> x.withOffset( x.offset+bodyStartOffset ))
                .forEach( context.messages::add );
        // fix AST node regions (if any)
        result.visitInOrder( (node,depth) -> {
            final TextRegion region = node.getRegion();
            if ( region != null ) {
                region.setStartingOffset( region.getStartingOffset()+bodyStartOffset );
            }
            if ( node instanceof LabelNode && ((LabelNode) node).isLocal ) {
                context.error("Macros may only use global labels (will be rewritten as necessary)",node);
            }
        });
        return result;
    }

    private DirectiveNode parseDirective()
    {
        if ( lexer.peek().is( TokenType.DOT ) )
        {
            final Token dot = lexer.next();
            if ( ! lexer.peek().is(TokenType.TEXT) ) {
                error("Expected a directive");
            }
            final Token directiveTok = lexer.next();
            TextRegion directiveRegion = directiveTok.region();
            directiveRegion.merge( dot.region() );

            final DirectiveNode result;
            switch( directiveTok.value )
            {
                case "macro":
                    return parseMacro(directiveRegion);
                case "reserve":
                    result = new DirectiveNode( DirectiveNode.Type.RESERVE, directiveRegion);
                    ASTNode node = parseExpression();
                    if ( node == null ) {
                        error("Missing argument");
                    }
                    result.add( node );
                    return result;
                case "byte": // .byte 1,2,3 / .word 1234,5678
                case "word": // .byte 1,2,3
                    DirectiveNode.Type type = directiveTok.value.equals("byte") ?
                            DirectiveNode.Type.BYTE : DirectiveNode.Type.WORD;
                    result = new DirectiveNode( type, directiveRegion );

                    boolean valueRequired = true;
                    do
                    {
                        node = parseExpression();
                        if ( node == null ) {
                            if ( valueRequired ) {
                                error("Expected a value");
                            }
                            break;
                        }
                        result.add( node );
                        valueRequired = false;
                        if ( lexer.peek().is(TokenType.COMMA ) ) {
                            lexer.next();
                            valueRequired = true;
                        }
                    } while (true);
                    return result;
                case "origin": // .origin 0x1234
                    ASTNode address = parseExpression();
                    if ( address == null ) {
                        error("Expected an argument");
                    }
                    result = new DirectiveNode( DirectiveNode.Type.ORIGIN, directiveRegion );
                    result.add( address );
                    return result;
                case "alias": // .alias v0 = x
                    if ( ! lexer.peek().is( TokenType.REGISTER ) ) {
                        error("Expected a register name (v0...v15) but got "+lexer.peek());
                    }
                    result = new DirectiveNode( DirectiveNode.Type.ALIAS, directiveRegion );
                    final Token registerToken = lexer.next();
                    final int regNum = Integer.parseInt( registerToken.value.substring( 1 ) );
                    result.add( new RegisterNode( regNum , registerToken.region() ) );
                    if ( ! lexer.peek().is(TokenType.EQUALS) ) {
                        error("Expected an '=' character but got "+lexer.peek());
                    }
                    lexer.next(); // consume '='

                    ASTNode value = parseIdentifier();
                    if ( value == null ) {
                        error("Expected an identifier but got "+lexer.peek());
                    }
                    result.add( value );
                    return result;
                case "clearAliases":
                    result = new DirectiveNode( DirectiveNode.Type.CLEAR_ALIASES, directiveRegion);
                    final List<ASTNode> nodes = parseExpressionList(false);
                    nodes.forEach( node1 ->
                    {
                        if ( ! ( node1 instanceof RegisterNode || node1 instanceof IdentifierNode ) )
                        {
                            error("Bad argument, only registers or identifiers are allowed",node1);
                        }
                        // TODO: Adding nodes of unexpected type here will break later compilation phases but
                        // removing all of them (if none happened to be of the right type) will also
                        // yield unwanted behaviour, namely clearing of all aliases
                        result.add( node1 );
                    } );
                    return result;
                case "equ": // .equ a = b
                    if ( ! Identifier.isValid( lexer.peek().value ) ) {
                        error("Expected an identifier but got "+lexer.peek());
                    }
                    result = new DirectiveNode( DirectiveNode.Type.EQU, directiveRegion );

                    ASTNode identifier = parseIdentifier();
                    if ( identifier == null ) {
                        error("Expected an identifier but got "+lexer.peek());
                    }
                    result.add( identifier );
                    if ( ! lexer.peek().is(TokenType.EQUALS) ) {
                        error("Expected an '=' character but got "+lexer.peek());
                    }
                    lexer.next(); // consume '='

                    value = parseExpression();
                    if ( value == null ) {
                        error("Expected an expression but got "+lexer.peek());
                    }
                    result.add( value );
                    return result;
            }
            error("Unrecognized directive @ "+directiveTok);
        }
        return null;
    }

    private IdentifierNode parseIdentifier()
    {
        final Token tok = lexer.peek();
        if ( tok.is( TokenType.IDENTIFIER ) ) {
            lexer.next();
            if (Identifier.isReserved( tok.value ) ) {
                error("'"+tok.value+"' is a reserved identifier and cannot be used here : "+tok);
            }
            return new IdentifierNode( Identifier.of( tok.value ), tok.region() );
        }
        return null;
    }

    private boolean isMacroInvocation(Token token)
    {
        if ( token.is(TokenType.IDENTIFIER)) {
            final Identifier id = Identifier.of( lexer.peek().value );
            final SymbolTable.Symbol symbol = context.symbolTable.get( SymbolTable.GLOBAL_SCOPE, id );
            return symbol != null && symbol.hasType( SymbolTable.Symbol.Type.MACRO );
        }
        return false;
    }

    private ASTNode parseIdentifierOrMacroInvocation()
    {
        if ( lexer.peek().is(TokenType.IDENTIFIER) )
        {
            final Token idToken = lexer.next();
            if (Identifier.isReserved( idToken.value ) ) {
                error("'"+idToken.value+"' is a reserved identifier and cannot be used here");
            }
            final Identifier id = Identifier.of( idToken.value );
            final IdentifierNode idNode = new IdentifierNode( id, idToken.region() );

            final SymbolTable.Symbol symbol = context.symbolTable.get( SymbolTable.GLOBAL_SCOPE, id );
            if ( symbol == null || !symbol.hasType( SymbolTable.Symbol.Type.MACRO ) )
            {
                return idNode;
            }

            final MacroInvocationNode inv = new MacroInvocationNode();
            inv.add( idNode );
            final boolean expectParensClose = lexer.peek().is( TokenType.PARENS_OPEN );
            if ( expectParensClose )
            {
                lexer.next(); // consume '('
            }
            inv.add( parseExpressionList( false ) );
            if ( expectParensClose )
            {
                if ( !lexer.peek().is( TokenType.PARENS_CLOSE ) )
                {
                    error( "Expected closing parentheses" );
                }
                else
                {
                    lexer.next(); // consume ')'
                }
            }
            return inv;
        }
        return null;
    }

    private List<ASTNode> parseExpressionList(boolean atLeastOneArgumentRequired)
    {
        final List<ASTNode> result = new ArrayList<>();
        ASTNode node = parseExpression();
        if ( node == null ) {
            if ( atLeastOneArgumentRequired ) {
                error("Expected at least one argument ");
            }
            return result;
        }
        result.add( node );
        while ( lexer.peek().is(TokenType.COMMA) )
        {
            lexer.next(); // consume comma
            node = parseExpression();
            if ( node == null ) {
                error("Trailing comma");
                return result;
            }
            result.add( node );
        }
        return result;
    }

    private ASTNode parseComment()
    {
        final Token tok = lexer.peek();
        if ( tok.is(TokenType.SEMICOLON) )
        {
            lexer.next();
            lexer.setSkipWhitespace( false );
            TextRegion region = tok.region();
            final StringBuilder comment = new StringBuilder();
            try {
                while ( ! lexer.eof() && ! lexer.peek().is(TokenType.NEWLINE) )
                {
                    final Token tok2 = lexer.next();
                    region.merge(tok2.region());
                    comment.append( tok2.value );
                }
            } finally {
                lexer.setSkipWhitespace( true );
            }
            return new CommentNode(comment.toString(), region);
        }
        return null;
    }

    public enum OperandType
    {
        NONE,
        REGISTER, // Vx
        REGISTER_V0, // V0
        NIBBLE,// 4-bit value
        BYTE,// 8-bit value
        ADDRESS, // 12-bit value
        // -- literals
        PRESSED_KEY, // literal 'K'
        INDEX, // literal 'I'
        DELAY_TIMER, //  literal 'DT'
        FONT,// literal 'F'
        BCD, //  literal 'B'
        SOUND_TIMER, // literal 'ST'
        I_INDIRECT; // literal '[I]'

        public boolean matches(ASTNode node, Assembler.CompilationContext context)
        {
            if ( node instanceof IdentifierNode) {

                final Identifier id = ((IdentifierNode) node).identifier;
                // try local scope first
                SymbolTable.Symbol symbol = context.symbolTable.get( context.getLastGlobalLabel() , id );
                if ( symbol == null )
                {
                    // fall-back to global scope
                    symbol = context.symbolTable.get( SymbolTable.GLOBAL_SCOPE, id );
                }
                if ( symbol == null ) {
                    context.error("Unknown symbol '"+id.value+"'", node);
                    return false;
                }
                Object value = symbol.value;
                switch( symbol.type ) {

                    case LABEL:
                    case EQU:
                        if ( value instanceof Number) {
                            return checkNumberInRange( ((Number) value).intValue() );
                        }
                        return this == ADDRESS;
                    case REGISTER_ALIAS:
                        if ( Integer.valueOf(0).equals( symbol.value ) )
                        {
                            return this == REGISTER_V0 || this == REGISTER;
                        }
                        return this == REGISTER;
                }
                return false;
            }
            if ( node instanceof TextNode )
            {
                // first, check if this is a register alias
                final String text = ((TextNode) node).value;
                switch( text.toLowerCase() ) {
                    case "k":
                        return this == PRESSED_KEY;
                    case "i":
                        return this == INDEX;
                    case "dt":
                        return this == DELAY_TIMER;
                    case "st":
                        return this == SOUND_TIMER;
                    case "f":
                        return this == FONT;
                    case "b":
                        return this == BCD;
                    case "[i]":
                        return this == I_INDIRECT;
                }
            }
            if ( node instanceof RegisterNode)
            {
                final int regNum = ((RegisterNode) node).regNum;
                if ( regNum == 0 && this == REGISTER_V0 ) {
                    return true;
                }
                return this == REGISTER;
            }

            if ( ExpressionEvaluator.isValueNode( node, context) )
            {
                final Object value = ExpressionEvaluator.evaluate( node, context, true );
                if ( value instanceof Number)
                {
                    int v = ((Number) value).intValue();
                    if ( checkNumberInRange( v ) ) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean checkNumberInRange(int v)
        {
            v = fixSigned( v );
            if ( this == NIBBLE ) {
                return v >= 0 && v <= 15;
            }
            if ( this == BYTE) {
                return v >= 0 && v <= 255;
            }
            if ( this == ADDRESS) {
                return v >= 0 && v <= 0xfff;
            }
            return false;
        }
    }

    private static int fixSigned(int v)
    {
        if ( (v&1<<31) != 0 ) { // MSB set => negative number
            if ( (v & 0xffff0000) == 0xffff0000) {
                v &= 0xffff;
                if ( (v & 0x0000ff00) == 0x0000ff00) {
                    v &= 0xff;
                }
            }
        }
        return v;
    }

    public enum Instruction
    {
        CLS("cls") {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this,0x00e0 );
            }
        },
        RET("ret") {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this, 0x00ee );
            }
        },
        JP("jp",OperandType.ADDRESS)
                {
                    @Override
                    public void compile(InstructionNode instruction, Assembler.CompilationContext context)
                    {
                        context.writeWord( this, 0x1000 | assertIn12BitRange( evaluate( instruction.child( 0 ), context ) , instruction, context ) );
                    }
                },
        CALL("call",OperandType.ADDRESS) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this,0x2000 | assertIn12BitRange( evaluate( instruction.child( 0 ), context ), instruction, context ) );
            }
        },
        SSE("sse",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 3xkk - SSE Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x3000 | regNo << 8 | cnst );
            }
        },
        SNE("SNE",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 4xkk - SE Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x4000 | regNo << 8 | cnst );
            }
        },
        SE2("SE",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 5xy0 - SE Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x5000 | regNo0 << 8 | regNo1 << 4);
            }
        },
        LD("LD",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 6xkk - LD Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x6000 | regNo << 8 | cnst );
            }
        },
        ADD("ADD",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 7xkk - ADD Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x7000 | regNo << 8 | cnst );
            }
        },
        LD2("LD",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy0 - LD Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8000 | regNo0 << 8 | regNo1 << 4);
            }
        },
        OR("OR",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy1 - OR Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8001 | regNo0 << 8 | regNo1 << 4);
            }
        },
        AND("AND",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy2 - AND Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8002 | regNo0 << 8 | regNo1 << 4);
            }
        },
        XOR("XOR",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy3 - XOR Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8003 | regNo0 << 8 | regNo1 << 4);
            }
        },
        ADD2("ADD",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy4 - ADD Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8004 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SUB("SUB",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy5 - SUB Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8005 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SHR("SHR",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy6 - SHR Vx,Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8006 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SHR2("SHR",OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy6 - SHR Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = regNo0;
                context.writeWord(this, 0x8006 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SUBN("SUN",OperandType.REGISTER,OperandType.REGISTER)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy7 - SUBN Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x8007 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SHL("SHL",OperandType.REGISTER,OperandType.REGISTER)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xyE - SHL Vx,Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x800e | regNo0 << 8 | regNo1 << 4);
            }
        },
        SHL2("SHL",OperandType.REGISTER)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xyE - SHL Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = regNo0;
                context.writeWord(this, 0x800e | regNo0 << 8 | regNo1 << 4);
            }
        },
        SNE2("SNE",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 9xy0 - SNE Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0x9000 | regNo0 << 8 | regNo1 << 4);
            }
        },
        LD3("LD",OperandType.INDEX,OperandType.ADDRESS) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Annn - LD I, addr
                int cnst = assertIn12BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xA000 | cnst );
            }
        },
        JP2("JP",OperandType.REGISTER_V0,OperandType.ADDRESS) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Bnnn - JP V0, addr
                int cnst = assertIn12BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xB000 | cnst );
            }
        },
        RND("RND",OperandType.REGISTER,OperandType.BYTE)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Cxkk - RND Vx, byte
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xc000 | regNo0 << 8 | cnst );
            }
        },
        DRW("DRW",OperandType.REGISTER,OperandType.REGISTER,OperandType.NIBBLE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Dxyn - DRW Vx, Vy, nibble
                int x = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                int y = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                int h = assertIn4BitRange( evaluate( instruction.child(2), context ), instruction, context );
                context.writeWord(this, 0xd000 | x << 8 | y << 4 | h);
            }
        },
        SKP("SKP",OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Ex9E - SKP Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                context.writeWord(this, 0xe09e | regNo0 << 8 );
            }
        },
        SKNP("SKNP",OperandType.REGISTER){
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // ExA1 - SKNP Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                context.writeWord(this, 0xe0a1 | regNo0 << 8 );
            }
        },
        LD4("LD",OperandType.REGISTER,OperandType.DELAY_TIMER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx07 - LD Vx, DT
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                context.writeWord(this, 0xf007 | regNo0 << 8 );
            }
        },
        LD5("LD",OperandType.REGISTER,OperandType.PRESSED_KEY ) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx0A - LD Vx, K
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                context.writeWord(this, 0xf00a | regNo0 << 8 );
            }
        },
        LD6("LD",OperandType.DELAY_TIMER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx15 - LD DT, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xf015 | regNo0 << 8 );
            }
        },
        LD7("LD",OperandType.SOUND_TIMER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx18 - LD ST, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xf018 | regNo0 << 8 );
            }
        },
        ADD3("ADD",OperandType.INDEX,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx1E - ADD I, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xf01e | regNo0 << 8 );
            }
        },
        LD8("LD",OperandType.FONT,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx29 - LD F, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xf029 | regNo0 << 8 );
            }
        },
        LD9("LD",OperandType.BCD,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx33 - LD B, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xf033 | regNo0 << 8 );
            }
        },
        LD10("LD",OperandType.I_INDIRECT,OperandType.REGISTER){
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx55 - LD [I], Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ), instruction, context );
                context.writeWord(this, 0xf055 | regNo0 << 8 );
            }
        },
        LD11("LD",OperandType.REGISTER,OperandType.I_INDIRECT){
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx65 - LD Vx, [I]
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ), instruction, context );
                context.writeWord(this, 0xf065 | regNo0 << 8 );
            }
        };

        public final String mnemonic;
        public final OperandType operand0;
        public final OperandType operand1;
        public final OperandType operand2;

        private Instruction(String mnemonic)
        {
            this(mnemonic,OperandType.NONE,OperandType.NONE);
        }

        public OperandType operandType(int no) {
            switch(no) {
                case 0:
                    return operand0;
                case 1:
                    return operand1;
                case 2:
                    return operand2;
            }
            throw new IllegalArgumentException( "Operand no. out-of-range: "+no );
        }

        protected int assertIn12BitRange(int value,ASTNode node, Assembler.CompilationContext context)
        {
            value = fixSigned( value );
            if ( value < 0 || value > 0xfff) {
                context.error( "Value not in 12-bit range: 0x"+Integer.toHexString( value ), node );
            }
            return value;
        }

        protected int assertIn8BitRange(int value, ASTNode node, Assembler.CompilationContext context)
        {
            value = fixSigned( value );
            if ( value < 0 || value > 0xff) {
                context.error( "Value not in 8-bit range: 0x"+Integer.toHexString( value ), node );
            }
            return value;
        }

        protected int assertIn4BitRange(int value,ASTNode node, Assembler.CompilationContext context)
        {
            value = fixSigned( value );
            if ( value < 0 || value > 0b1111) {
                context.error( "Value not in 4-bit range: 0x"+Integer.toHexString( value ),node );
            }
            return value;
        }

        public static Instruction findMatch(InstructionNode insn, Assembler.CompilationContext context)
        {
            final List<Instruction> candidates =
                    Stream.of( values() ).filter( x -> matchesOperandTypes( insn, x, context ) ).collect( Collectors.toList() );
            switch ( candidates.size() ) {
                case 0 :
                    return null;
                case 1:
                    return candidates.get(0);
                default:
                    throw new RuntimeException("Internal error, found multiple candidate instructions: "+candidates);
            }
        }

        protected int evaluate(ASTNode node, Assembler.CompilationContext ctx)
        {
            if ( node instanceof RegisterNode ) {
                return ((RegisterNode) node).regNum;
            }
            return ExpressionEvaluator.evaluateNumber( node,ctx,true );
        }

        public abstract void compile(InstructionNode instruction, Assembler.CompilationContext context);

        private static boolean matchesOperandTypes(InstructionNode node,Instruction insn,Assembler.CompilationContext context)
        {
            if ( node.childCount() != insn.operandCount() ) {
                return false;
            }
            if ( ! insn.mnemonic.equalsIgnoreCase( node.mnemonic ) ) {
                return false;
            }
            for ( int i = 0, len = insn.operandCount() ; i < len ; i++ )
            {
                OperandType expected = insn.operandType( i );
                final boolean matches = expected.matches( node.child( i ), context );
                if ( ! matches )
                {
                    System.out.println( insn.name() + ",operand " + i + " (" + expected + ") does NOT match " + node.child( i ) );
                }
                if ( ! matches ) {
                    return false;
                }
            }
            return true;
        }

        private Instruction(String mnemonic,OperandType op0)
        {
            this(mnemonic,op0,OperandType.NONE);
        }

        private Instruction(String mnemonic,OperandType op0,OperandType op1)
        {
            this(mnemonic,op0,op1,OperandType.NONE);
        }

        private Instruction(String mnemonic,OperandType op0,OperandType op1,OperandType op2)
        {
            this.mnemonic = mnemonic;
            operand0 = op0;
            operand1 = op1;
            operand2 = op2;
        }

        public OperandType operand0() {
            return operand0;
        }

        public OperandType operand1() {
            return operand1;
        }

        public OperandType operand2() {
            return operand2;
        }

        public int operandCount()
        {
            int count = 0;
            if ( operand0 != OperandType.NONE ) {
                count++;
            }
            if ( operand1 != OperandType.NONE ) {
                count++;
            }
            if ( operand2 != OperandType.NONE ) {
                count++;
            }
            return count;
        }
    }

    private void printError(String msg,int offset)
    {
        final String text = lexer.getScanner().getText();
        final String[] lines = text.split("\n");
        int lineStartOffset = 0;
        int lineNo = 1;
        for ( String line : lines )
        {
            final int lineEnd = lineStartOffset + line.length();
//            System.err.println("Line "+lineNo+" ("+lineStartOffset+" - "+lineEnd+"): "+line);
            if ( lineStartOffset <= offset && offset <= lineEnd )
            {
                int regionStart = Math.max( lineStartOffset, offset-10);
                int regionEnd = Math.min( lineEnd, offset+10);
                final String errLine =
                        line.substring( regionStart-lineStartOffset, regionEnd-lineStartOffset );
                final int col = offset - lineStartOffset;
                System.err.println( "Error at line "+lineNo+", column "+col);
                System.err.println( errLine );
                final int indent = offset - regionStart;
                System.err.println( StringUtils.repeat(' ',indent)+"^ "+msg);
                return;
            }
            lineStartOffset = lineEnd+1;
            lineNo++;
        }
        if ( lines.length > 0 && offset == lineStartOffset)
        {
            final int col = offset - lineStartOffset;
            System.err.println( "Error at line "+lineNo+", column "+col);
            System.err.println( lines[lines.length - 1] );
        }
        else
        {
            System.err.println( "Failed to find src line for offset " + offset );
        }
        System.err.println("ERROR: "+msg);
    }

    private void skipNewlines()
    {
        while( lexer.peek().is(TokenType.NEWLINE ) ) {
            lexer.next(); // consume newline
        }
    }
}