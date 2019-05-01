package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;

public class Assembler
{
    private static final int UNKNOWN_SIZE = -1;

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

        private String hex(int value) {
            return StringUtils.leftPad(Integer.toHexString( value),4,'0' );
        }

        public void writeWord(Parser.Instruction insn, int word) {
            try
            {
                System.out.println( hex(currentAddress)+": "+insn.name()+" - 0x"+hex( word ) );
                executable.write( (word & 0xff00)>>>8);
                executable.write( (word & 0xff) );
                currentAddress += 2;
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

    public byte[] assemble(ASTNode ast,int startAddress)
    {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        compilationContext = new CompilationContext( bos );
        compilationContext.currentAddress = startAddress;

        // assign addresses to labels
        ast.visitInOrder( (node,depth) ->
        {
            if ( node instanceof LabelNode )
            {
                compilationContext.symbolTable.add( ((LabelNode) node).id , compilationContext.currentAddress );
            }
            else if ( node instanceof InstructionNode )
            {
                compilationContext.currentAddress+=2;
            }
        });

        // now generate binary
        compilationContext.currentAddress = startAddress;
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

    public static void main(String[] args) throws IOException
    {
        if ( args.length > 0 )
        {
            assemble(args);
        }
        else
        {
            final File base = new File( "/home/tobi/intellij_workspace/chip8" );
            assemble( new File( base, "sample_sources/test.chip8" ),
                    new File( base, "src/main/resources/test.ch8" ) );
        }
    }

    public static void assemble(String[] args) throws IOException
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
        assemble(src,out);
    }

    public static int assemble(File src, File out) throws IOException
    {
        if (!src.exists())
        {
            throw new RuntimeException("Source file does not exist: " + src.getAbsolutePath());
        }

        final String srcCode = new String(Files.readAllBytes(src.toPath()));
        return assemble( srcCode, new FileOutputStream(out) );
    }

    public static int assemble(String srcCode, OutputStream out) throws IOException
    {
        Validate.notNull(srcCode, "srcCode must not be null");
        Validate.notNull(out, "out must not be null");

        final Parser p = new Parser( new Lexer( new Scanner( srcCode ) ) );
        final ASTNode ast = p.parse();
        System.out.println("---- AST ---\n");
        ast.visitInOrder(  (node,depth) -> {
            System.out.println( StringUtils.repeat(' ',depth)+" "+node);
        });
        final byte[] binary = new Assembler().assemble( ast, 0x200 );

        try ( out ) {
            out.write( binary );
        }
        System.out.println("Wrote "+binary.length+" bytes.");
        return binary.length;
    }
}