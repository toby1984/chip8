package de.codesourcery.chip8.asm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Assembler
{
    private static final int UNKNOWN_SIZE = -1;

    private final ASTNode ast;
    private final int startAddress;

    private CompilationContext compilationContext;

    public static final class CompilationContext
    {
        public final SymbolTable symbolTable = new SymbolTable();
        public final OutputStream executable;
        public int currentAddress;

        public CompilationContext(OutputStream executable)
        {
            this.executable = executable;
        }

        public void write(int value) {
            try
            {
                executable.write( (value & 0xff00)>>>8);
                executable.write( (value & 0xff) );
            }
            catch (IOException e)
            {
                throw new RuntimeException("Failed to write binary",e);
            }
        }
    }

    enum Phase
    {
        CALCULATE_ADDRESSES
    }

    public Assembler(ASTNode ast, int startAddress)
    {
        this.ast = ast;
        this.startAddress = startAddress;
    }

    public byte[] assemble()
    {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        compilationContext = new CompilationContext( bos );

        // assign addresses to labels
        ast.visitInOrder( (node,depth) ->
        {
            if ( node instanceof LabelNode)
            {
                compilationContext.symbolTable.add( ((LabelNode) node).id , compilationContext.currentAddress );
            }
            else if ( node instanceof InstructionNode )
            {
                compilationContext.currentAddress+=2;
            }
        });

        // now generate binary
        ast.visitInOrder( (node,depth) ->
        {
            if ( node instanceof InstructionNode)
            {
                final Parser.Instruction instruction = ((InstructionNode) node).getInstruction();
                instruction.compile( (InstructionNode) node,compilationContext);
            }
        });
        return bos.toByteArray();
    }
}