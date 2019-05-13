/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.AST;
import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.DirectiveNode;
import de.codesourcery.chip8.asm.ast.IdentifierNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.ast.MacroDeclarationNode;
import de.codesourcery.chip8.asm.ast.MacroInvocationNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.ast.TextRegion;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import de.codesourcery.chip8.asm.parser.Token;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Crude CHIP-8 assembler.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Assembler
{
    private static long globalUniqueIdentifier = 0;
    private CompilationContext compilationContext;

    public static final class NOPOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException
        {
        }
    }
    public static void main(String[] args) throws IOException
    {
        if ( args.length != 2 )
        {
            throw new RuntimeException( "Usage: <source file> <binary>" );
        }
        File src = new File( args[0] );
        if ( !src.exists() )
        {
            throw new RuntimeException( "Source file does not exist: " + src.getAbsolutePath() );
        }
        final File out = new File( args[1] );
        Assembler.assemble( src, out );
    }

    /**
     * Compile source from/to a file.
     *
     * @param src source to compile
     * @param out output stream to write binary to
     * @return number of bytes written to the output stream
     * @throws IOException
     */
    public static void assemble(File src, File out) throws IOException
    {
        if ( !src.exists() )
        {
            throw new RuntimeException( "Source file does not exist: " + src.getAbsolutePath() );
        }

        final String srcCode = new String( Files.readAllBytes( src.toPath() ) );
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try ( final ExecutableWriter writer = new ExecutableWriter( outStream ) )
        {
            final CompilationContext ctx = new Assembler().assemble( srcCode, 0x200, writer );

            for (CompilationMessages.CompilationMessage msg : ctx.messages.getSorted() ) {
                System.out.println( msg );
            }
            if ( ctx.messages.hasErrors() )
            {
                System.exit(1);
            }
            else
            {
                try ( FileOutputStream fOut = new FileOutputStream( out ) ) {
                    fOut.write( outStream.toByteArray() );
                }
                System.out.println("Wrote "+writer.getBytesWritten()+" bytes.");
                System.exit( 0 );
            }
        }
    }

    /**
     * Compile source from a string and write it to an {@link OutputStream}.
     *
     * @param source source to compile
     * @param out     output stream to write binary to
     * @return compilation context with (error) messages, symbol table etc.
     */
    public CompilationContext assemble(String source, int startAddress,ExecutableWriter out)
    {
        final CompilationContext ctx = new CompilationContext( out );
        assemble(source,ctx,startAddress);
        return ctx;
    }

    private void assemble(String source, CompilationContext context, int startAddress)
    {
        context.info("Compilation started @ "+ ZonedDateTime.now(),-1 );

        compilationContext = context;
        compilationContext.currentAddress = startAddress;

        // parse phase
        final List<CompilationPhase> phases = List.of( new ParseSourcePhase( source ),
                new ExpandMacrosPhase(),
                new AssignSymbolsPhase(),
                new GenerateCodePhase(startAddress) );

        for (Iterator<CompilationPhase> it = phases.iterator();
             it.hasNext() && ! compilationContext.hasErrors() ; )
        {
            final CompilationPhase phase = it.next();
            phase.perform();
        }

        if ( compilationContext.messages.hasErrors() ) {
            context.error("Compilation finished with errors ",-1 );
        } else {
            context.info("Compilation finished ("+compilationContext.outputWriter.getBytesWritten()+" bytes)",-1 );
        }
    }

    public static final class CompilationContext implements ISymbolResolver
    {
        public final SymbolTable symbolTable = new SymbolTable();
        public final ExecutableWriter outputWriter;
        Identifier lastGlobalLabel = SymbolTable.GLOBAL_SCOPE;
        public final CompilationMessages messages = new CompilationMessages();
        int currentAddress;
        public AST ast;

        public CompilationContext(ExecutableWriter writer)
        {
            this.outputWriter= writer;
        }

        @Override
        public SymbolTable.Symbol get(Identifier name)
        {
            // try local scope first
            SymbolTable.Symbol symbol = symbolTable.get( getLastGlobalLabel(), name );
            if ( symbol == null ) {
                // fall-back to global scope
                symbol = symbolTable.get( name );
            }
            return symbol;
        }

        @Override
        public SymbolTable.Symbol get(Identifier scope, Identifier name)
        {
            return symbolTable.get(scope,name);
        }

        public Identifier getLastGlobalLabel()
        {
            return lastGlobalLabel == null ? SymbolTable.GLOBAL_SCOPE : lastGlobalLabel;
        }

        public Identifier generateUniqueIdentifier()
        {
            return Identifier.of( "tmp_"+(globalUniqueIdentifier++));
        }

        private String hex(int value)
        {
            return StringUtils.leftPad( Integer.toHexString( value ), 4, '0' );
        }

        public void info(String message, int offset) {
            messages.info(message, offset);
        }

        public void info(String message, TextRegion region) {
            messages.info(message, region );
        }

        public void info(String message, ASTNode node) {
            messages.info(message, node );
        }

        public void info(String message, Token token) {
            messages.info(message, token );
        }

        public void warn(String message, int offset) {
            messages.warn(message, offset);
        }

        public void warn(String message,TextRegion region) {
            messages.warn(message, region );
        }

        public void warn(String message,ASTNode node) {
            messages.warn(message, node );
        }

        public void warn(String message,Token token) {
            messages.warn(message, token );
        }

        public void error(String message, int offset) {
            messages.error(message, offset);
        }

        public void error(String message,TextRegion region) {
            messages.error(message, region );
        }

        public void error(String message,ASTNode node) {
            messages.error(message, node );
        }

        public void error(String message,Token token) {
            messages.error(message, token );
        }

        public void writeByte(int iValue)
        {
            try
            {
                outputWriter.writeByte( iValue );
                currentAddress += 1;
            }
            catch (IOException e)
            {
                throw new RuntimeException("Failed to write binary",e);
            }
        }

        public void reserveBytes(int count)
        {
            for ( int i = count ; i > 0 ; i--)
            {
                writeByte( 0 );
            }
        }

        public void writeWord(Parser.Instruction insn, int word)
        {
            try
            {
                if ( insn != null )
                {
                    System.out.println( hex( currentAddress ) + ": " + insn.name() + " - 0x" + hex( word ) );
                } else {
                    System.out.println( hex( currentAddress ) + ": " + hex( word ) );
                }
                outputWriter.writeByte( (word & 0xff00) >>> 8 );
                outputWriter.writeByte( (word & 0xff) );
                currentAddress += 2;
            } catch (IOException e)
            {
                throw new RuntimeException( "Failed to write binary", e );
            }
        }

        public boolean hasErrors()
        {
            return messages.hasErrors();
        }
    }

    public final class ExpandMacrosPhase extends CompilationPhase {

        @Override
        public void visit(ASTNode node, ASTNode.IterationContext ctx)
        {
            if ( node instanceof MacroInvocationNode )
            {
                final Set<Identifier> alreadyExpanded = new HashSet<>();
                final ASTNode expanded = expandRecursively( (MacroInvocationNode) node, alreadyExpanded );
                node.replaceWith( expanded );
            }
        }

        private ASTNode expandRecursively( MacroInvocationNode invocation, Set<Identifier> alreadyExpanded )
        {
            if ( alreadyExpanded.contains( invocation.getMacroName() ) ) {
                compilationContext.error("Infinite recursion during macro expansion",invocation);
                return invocation;
            }
            System.out.println("==== Expanding "+invocation.getMacroName()+" ====");
            System.out.println( invocation.toPrettyString() );

            alreadyExpanded.add( invocation.getMacroName() );
            final ASTNode body = Parser.expandMacroInvocation( invocation, compilationContext );
            body.visitInOrder( (n,depth) ->
            {
                if ( n instanceof MacroInvocationNode )
                {
                    ASTNode expanded = expandRecursively( (MacroInvocationNode) n, alreadyExpanded );
                    n.replaceWith( expanded );
                }
            });
            System.out.println("==== Expanded "+invocation.getMacroName()+" ====");
            System.out.println( body.toPrettyString() );
            return body;
        }
    }

    public final class ParseSourcePhase extends CompilationPhase {

        private final String source;

        public ParseSourcePhase(String source) {
            this.source = source;
        }

        @Override
        public void perform()
        {
            final Lexer lexer = new Lexer( new Scanner( source ) );
            final Parser p = new Parser( lexer, compilationContext );
            compilationContext.ast = (AST) p.parse();

            System.out.println( "---- AST ---\n" );
            compilationContext.ast.visitInOrder( (node, depth) ->
            {
                System.out.println( StringUtils.repeat( ' ', depth ) + " " + node );
            });
        }

        @Override
        public void visit(ASTNode node, ASTNode.IterationContext ctx)
        {
            // nothing to be done here
        }
    }

    public abstract class CompilationPhase implements ASTNode.Visitor2
    {
        public void perform()
        {
            compilationContext.ast.visitInOrder2( this );
        }

        protected final void visitDirective(ASTNode node, boolean generateCode)
        {
            if ( node instanceof DirectiveNode )
            {
                final DirectiveNode directive = (DirectiveNode) node;
                switch ( directive.type )
                {
                    case CLEAR_ALIASES:
                        final Set<Identifier> identifiers = new HashSet<>();
                        final Set<Integer> registers = new HashSet<>();

                        for (ASTNode n : node.children)
                        {
                            if ( n instanceof RegisterNode )
                            {
                                registers.add(((RegisterNode) n).regNum);
                            }
                            else if ( n instanceof IdentifierNode )
                            {
                                identifiers.add( ((IdentifierNode) n).identifier );
                            }
                            else
                            {
                                throw new IllegalArgumentException( "Unsupported AST node: " + node );
                            }
                        }
                        final Predicate<SymbolTable.Symbol> toRemove =
                                symbol -> registers.contains( symbol.value ) || identifiers.contains( symbol.name );
                        compilationContext.symbolTable.clearAliases(toRemove);
                        break;
                    case ALIAS:
                        final RegisterNode regNode = (RegisterNode) node.child( 0 );
                        final IdentifierNode idNode = (IdentifierNode) node.child( 1 );
                        compilationContext.symbolTable.redefine( compilationContext.getLastGlobalLabel(),
                                idNode.identifier,
                                SymbolTable.Symbol.Type.REGISTER_ALIAS, regNode.regNum );
                        break;
                    case ORIGIN:
                        visitOrigin( node );
                        break;
                    case BYTE:
                        if ( generateCode )
                        {
                            for (ASTNode arg : directive.children)
                            {
                                final Integer iValue = ExpressionEvaluator.evaluateByte( arg,
                                        new ExpressionEvaluator.NodeEvaluator(compilationContext), true );
                                compilationContext.writeByte( iValue );
                            }
                        }
                        else
                        {
                            compilationContext.currentAddress += directive.childCount();
                        }
                        // TODO: Alignment after writing an odd number of bytes?
                        break;
                    case WORD:
                        if ( generateCode )
                        {
                            for (ASTNode arg : directive.children)
                            {
                                final Integer value = ExpressionEvaluator.evaluateWord( arg,
                                        new ExpressionEvaluator.NodeEvaluator(compilationContext), true );
                                compilationContext.writeWord( null, value );
                            }
                        }
                        else
                        {
                            compilationContext.currentAddress += directive.childCount() * 2;
                        }
                        break;
                    case RESERVE:
                        final Integer value = ExpressionEvaluator.evaluateWord( directive.child( 0 ),
                                new ExpressionEvaluator.NodeEvaluator(compilationContext), true );
                        if ( generateCode )
                        {
                            compilationContext.reserveBytes( value );
                        } else {
                            compilationContext.currentAddress += value;
                        }
                        // TODO: Alignment after allocating an odd number of bytes?
                        break;
                }
            }
        }

        private final void visitOrigin(ASTNode node)
        {
            Integer newAddress = ExpressionEvaluator.evaluateAddress( node.child(0),
                    new ExpressionEvaluator.NodeEvaluator(compilationContext), true );
            if ( newAddress < compilationContext.currentAddress ) {
                throw new RuntimeException("Cannot set origin 0x"+Integer.toHexString( newAddress )+" which is before current address (0x"+Integer.toHexString( compilationContext.currentAddress ));
            }
            compilationContext.currentAddress = newAddress;
        }
    }

    public final class AssignSymbolsPhase extends CompilationPhase
    {
        @Override
        public void visit(ASTNode node, ASTNode.IterationContext ctx)
        {
            if ( node instanceof MacroDeclarationNode) {
                ctx.dontGoDeeper();
            }
            if ( node instanceof DirectiveNode )
            {
                if ( ((DirectiveNode) node).type == DirectiveNode.Type.EQU )
                {
                    final IdentifierNode identifierNode = (IdentifierNode) node.child( 0 );
                    final Object value = new ExpressionEvaluator.NodeEvaluator(compilationContext).evaluate(node.child(1), true);
                    compilationContext.symbolTable.define( SymbolTable.GLOBAL_SCOPE, identifierNode.identifier,
                            SymbolTable.Symbol.Type.EQU, value );
                }
                else
                {
                    visitDirective( node, false );
                }
            }
            else if ( node instanceof LabelNode )
            {
                final LabelNode ln = (LabelNode) node;
                if ( ln.isLocal )
                {
                    if ( compilationContext.lastGlobalLabel == SymbolTable.GLOBAL_SCOPE )
                    {
                        throw new RuntimeException( "Cannot define a local label without defining a global label " +
                                "first" );
                    }
                    compilationContext.symbolTable.define(
                            compilationContext.lastGlobalLabel,
                            ln.id,
                            SymbolTable.Symbol.Type.LABEL,
                            compilationContext.currentAddress );
                }
                else
                {
                    try
                    {
                        final SymbolTable.Symbol symbol = compilationContext.symbolTable.define(
                                SymbolTable.GLOBAL_SCOPE,
                                ln.id,
                                SymbolTable.Symbol.Type.LABEL,
                                compilationContext.currentAddress );
                        compilationContext.lastGlobalLabel = symbol.name;
                    }
                    catch (Exception e)
                    {
                        compilationContext.error( e.getMessage(), node );
                    }
                }
            }
            else if ( node instanceof InstructionNode )
            {
                compilationContext.currentAddress += 2;
            }
        }
    }

    public final class GenerateCodePhase extends CompilationPhase
    {
        public final int startAddress;

        public GenerateCodePhase(int startAddress ) {
            this.startAddress = startAddress;
        }

        @Override
        public void perform()
        {
            compilationContext.currentAddress = startAddress;
            super.perform();
        }

        @Override
        public void visit(ASTNode node, ASTNode.IterationContext ctx)
        {
            if ( node instanceof MacroDeclarationNode ) {
                ctx.dontGoDeeper();
            }
            else if ( node instanceof DirectiveNode )
            {
                visitDirective( node,true );
            }
            else if ( node instanceof LabelNode )
            {
                final LabelNode ln = (LabelNode) node;
                if ( ln.isGlobal() )
                {
                    compilationContext.lastGlobalLabel = ln.id;
                }
            }
            else if ( node instanceof InstructionNode )
            {
                final Parser.Instruction instruction =
                        ((InstructionNode) node).getInstruction( compilationContext );
                if ( instruction == null )
                {
                    compilationContext.error( "Unrecognized instruction @ " + node, node );
                }
                else
                {
                    instruction.compile( (InstructionNode) node, compilationContext );
                }
            }
        }
    }
}