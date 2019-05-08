package de.codesourcery.chip8.asm;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class AssemblerTest
{
    @Test
    public void assemble1() throws IOException
    {
        final String src =
                "ld v0,10\n" +
                        "ld v1,1\n" +
                        "label: \n" +
                        "sub v0,v1\n" +
                        "se v0,0\n" +
                        "jp label";
        assemble(src);
    }

    @Test
    public void assemble2() throws IOException
    {
        final String src =
                ".alias v0 = x\n" +
                        ".alias v1 = y\n" +
                        "ld v0,x\n" +
                        "ld v1,y\n" +
                        "label: \n" +
                        "sub x,y\n" +
                        "se x,0\n" +
                        "jp label";
        assemble(src);
    }

    @Test
    public void testWriteBytes() throws IOException
    {
        final String src =
                        ".byte %1,0x2,%101";
        final byte[] data = assemble( src );
        assertEquals( 3, data.length );
        assertEquals( (byte) 1, data[0] );
        assertEquals( (byte) 2, data[1] );
        assertEquals( (byte) 5, data[2] );
    }

    @Test
    public void testWriteWords() throws IOException
    {
        final String src =
                ".word %1,0x2,%101";
        final byte[] data = assemble( src );
        assertEquals( 6, data.length );
        assertEquals( (byte) 0, data[0] );
        assertEquals( (byte) 1, data[1] );
        assertEquals( (byte) 0, data[2] );
        assertEquals( (byte) 2, data[3] );
        assertEquals( (byte) 0, data[4] );
        assertEquals( (byte) 5, data[5] );
    }

    @Test
    public void testReserve() throws IOException
    {
        final String src =
                ".reserve 10";
        final byte[] data = assemble( src );
        assertEquals( 10, data.length );
        for ( int i =0 ; i < data.length ; i++)
        {
            assertEquals( (byte) 0, data[i] );
        }
    }

    @Test
    public void testLocalLabel() throws IOException
    {
        final String src =
            "global:\n" +
                "ld v0,10\n" +
                "ld v1,1\n" +
                ".loop\n" +
                "sub v0,v1\n" +
                "se v0,0\n" +
                "jp loop";
        assemble(src);
    }

    private byte[] assemble(String source) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Assembler().assemble( source, 0x200, new ExecutableWriter( out ) );
        return out.toByteArray();
    }
}