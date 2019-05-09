package de.codesourcery.chip8.asm;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class ExecutableWriter implements Closeable, AutoCloseable
{
    private final OutputStream out;
    private int bytesWritten;

    public ExecutableWriter()
    {
        this( new OutputStream() {

            @Override
            public void write(int b) throws IOException
            {
                // nothing to see here
            }
        });
    }

    public ExecutableWriter(OutputStream out) {
        this.out = out;
    }

    public void close() throws IOException {
        out.close();
    }

    public void writeByte(int value) throws IOException
    {
        out.write( value );
        bytesWritten++;
    }

    public int getBytesWritten()
    {
        return bytesWritten;
    }
}