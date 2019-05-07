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
        Assembler.assemble( source, out );
        return out.toByteArray();
    }
}