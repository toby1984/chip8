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

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.DirectiveNode;
import de.codesourcery.chip8.asm.ast.IdentifierNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Crude CHIP-8 assembler.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Assembler
{
    private CompilationContext compilationContext;

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
        assemble( src, out );
    }

    /**
     * Compile source from/to a file.
     *
     * @param src source to compile
     * @param out output stream to write binary to
     * @return number of bytes written to the output stream
     * @throws IOException
     */
    public static int assemble(File src, File out) throws IOException
    {
        if ( !src.exists() )
        {
            throw new RuntimeException( "Source file does not exist: " + src.getAbsolutePath() );
        }

        final String srcCode = new String( Files.readAllBytes( src.toPath() ) );
        return assemble( srcCode, new FileOutputStream( out ) );
    }

    /**
     * Compile source from a string and write it to an {@link OutputStream}.
     *
     * @param srcCode source to compile
     * @param out     output stream to write binary to
     * @return number of bytes written to the output stream
     * @throws IOException
     */
    public static int assemble(String srcCode, OutputStream out) throws IOException
    {
        Validate.notNull( srcCode, "srcCode must not be null" );
        Validate.notNull( out, "out must not be null" );

        final Parser p = new Parser( new Lexer( new Scanner( srcCode ) ) );
        final ASTNode ast = p.parse();
        System.out.println( "---- AST ---\n" );
        ast.visitInOrder( (node, depth) ->
                          {
                              System.out.println( StringUtils.repeat( ' ', depth ) + " " + node );
                          } );
        final byte[] binary = new Assembler().assemble( ast, 0x200 );

        try (out)
        {
            out.write( binary );
        }
        System.out.println( "Wrote " + binary.length + " bytes." );
        return binary.length;
    }

    private byte[] assemble(ASTNode ast, int startAddress)
    {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        compilationContext = new CompilationContext( bos );
        compilationContext.currentAddress = startAddress;

        // assign addresses to labels
        ast.visitInOrder( new AssignSymbolsPhase() );

        // now generate binary
        compilationContext.currentAddress = startAddress;
        ast.visitInOrder( new GenerateCodePhase() );
        return bos.toByteArray();
    }

    public static final class CompilationContext
    {
        public final SymbolTable symbolTable = new SymbolTable();
        final OutputStream executable;
        public Identifier lastGlobalLabel = SymbolTable.GLOBAL_SCOPE;
        int currentAddress;

        CompilationContext(OutputStream executable)
        {
            this.executable = executable;
        }

        public Identifier getLastGlobalLabel()
        {
            return lastGlobalLabel == null ? SymbolTable.GLOBAL_SCOPE : lastGlobalLabel;
        }

        private String hex(int value)
        {
            return StringUtils.leftPad( Integer.toHexString( value ), 4, '0' );
        }

        public void writeWord(Parser.Instruction insn, int word)
        {
            try
            {
                System.out.println( hex( currentAddress ) + ": " + insn.name() + " - 0x" + hex( word ) );
                executable.write( (word & 0xff00) >>> 8 );
                executable.write( (word & 0xff) );
                currentAddress += 2;
            } catch (IOException e)
            {
                throw new RuntimeException( "Failed to write binary", e );
            }
        }
    }

    public abstract class CompilationPhase implements ASTNode.Visitor
    {

        protected final void visitDirective(ASTNode node)
        {
            if ( node instanceof DirectiveNode )
            {
                if ( ((DirectiveNode) node).type == DirectiveNode.Type.ALIAS )
                {
                    final RegisterNode regNode = (RegisterNode) node.child( 0 );
                    final IdentifierNode idNode = (IdentifierNode) node.child( 1 );
                    compilationContext.symbolTable.redefine( compilationContext.getLastGlobalLabel(),
                                                             idNode.identifier,
                                                             SymbolTable.Symbol.Type.REGISTER_ALIAS, regNode.regNum );
                }
                else if ( ((DirectiveNode) node).type == DirectiveNode.Type.ORIGIN )
                {
                    visitOrigin(node);
                }
            }
        }

        private final void visitOrigin(ASTNode node)
        {
            Object value = ExpressionEvaluator.evaluate( node.child(0), compilationContext, true );
            if ( !(value instanceof Number) ) {

            }
            final int newAddress = ((Number) value).intValue();
            if ( newAddress < 0 || newAddress > 0xfff ) {
                throw new RuntimeException(".origin requires a value 0x00 - 0xfff but got 0x"+Integer.toHexString( newAddress ) );
            }
            if ( newAddress < compilationContext.currentAddress ) {
                throw new RuntimeException("Cannot set origin 0x"+Integer.toHexString( newAddress )+" which is before current address (0x"+Integer.toHexString( compilationContext.currentAddress ));
            }
            compilationContext.currentAddress = newAddress;
        }
    }

    public final class AssignSymbolsPhase extends CompilationPhase
    {
        @Override
        public void visit(ASTNode node, int depth)
        {
            if ( node instanceof DirectiveNode )
            {
                if ( ((DirectiveNode) node).type == DirectiveNode.Type.EQU )
                {
                    final IdentifierNode identifierNode = (IdentifierNode) node.child( 0 );
                    final Object value = ExpressionEvaluator.evaluate( node.child( 1 ), compilationContext, true );
                    compilationContext.symbolTable.define( SymbolTable.GLOBAL_SCOPE, identifierNode.identifier,
                                                           SymbolTable.Symbol.Type.EQU, value );
                }
                else
                {
                    visitDirective( node );
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
                    final SymbolTable.Symbol symbol = compilationContext.symbolTable.define(
                        SymbolTable.GLOBAL_SCOPE,
                        ln.id,
                        SymbolTable.Symbol.Type.LABEL,
                        compilationContext.currentAddress );
                    compilationContext.lastGlobalLabel = symbol.name;
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
        @Override
        public void visit(ASTNode node, int depth)
        {
            if ( node instanceof DirectiveNode )
            {
                visitDirective( node );
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
                    throw new RuntimeException( "Unknown instruction @ " + node );
                }
                instruction.compile( (InstructionNode) node, compilationContext );
            }
        }
    }
}