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
package de.codesourcery.chip8.emulator;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * The emulator's main memory.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Memory
{
    private final byte[] data;

    /**
     * Create instance.
     *
     * @param sizeInBytes
     */
    public Memory(int sizeInBytes)
    {
        this.data = new byte[ sizeInBytes ];
    }

    /**
     * Returns the size of this memory.
     * @return
     */
    public int getSizeInBytes() {
        return data.length;
    }

    /**
     * Read a byte.
     *
     * @param address
     * @return
     */
    public int read(int address) {
        return data[address] & 0xff;
    }

    /**
     * Copies a given number of bytes starting at a specific address to a byte array.
     *
     * @param address
     * @param count
     * @param destination
     */
    public void read(int address,int count,byte[] destination) {

        int adr = address % this.data.length;
        for ( int i = 0, writePtr = 0 ; i < count ; i++ )
        {
            destination[writePtr++] = this.data[adr];
            adr = (adr+1) % this.data.length;
        }
    }

    /**
     * Bulk-load memory from a file on the classpath.
     *
     * @param classpath
     * @param address start address to write data to
     * @return
     * @throws IOException
     */
    public int load(String classpath,int address) throws IOException
    {
        InputStream in = Memory.class.getResourceAsStream( classpath );
        if (in == null)
        {
            throw new FileNotFoundException("classpath:"+classpath);
        }
        return load(in,address);
    }

    /**
     * Bulk-load memory from a file an input stream.
     *
     * @param in
     * @param address start address to write data to
     * @return
     * @throws IOException
     */
    public int load(InputStream in, int address) throws IOException
    {
        Validate.notNull(in, "input stream must not be null");
        try (in)
        {
            final byte[] input = in.readAllBytes();
            System.arraycopy( input , 0 ,data, address, input.length);
            return input.length;
        }
    }

    /**
     * Write a byte.
     *
     * @param address
     * @param value value to write (only bits 0-7 are considered)
     */
    public void write(int address,int value) {
        this.data[address] = (byte) value;
    }

    /**
     * Bulk-copy a byte array into this memory.
     *
     * @param startAddress
     * @param data
     */
    public void write(int startAddress, byte[] data)
    {
        for ( int readPtr= 0,writePtr=startAddress,len=data.length ; readPtr < len; readPtr++,writePtr = (writePtr+1) % this.data.length )
        {
            this.data[writePtr] = data[readPtr];
        }
    }

    /**
     * Clear memory.
     *
     * All locations are set to zero.
     */
    public void reset()
    {
        Arrays.fill(data,(byte) 0);
    }

    private static String hexWord(int value) {
        return StringUtils.leftPad( Integer.toHexString(value & 0xfff), 4 , '0' );
    }

    private static String hexByte(int value) {
        return StringUtils.leftPad( Integer.toHexString(value & 0xff), 2 , '0' );
    }

    public String dump(int offset, int count,int bytesPerRow) {
        return dump(offset, this.data,count, bytesPerRow);
    }

    /**
     * Dump byte array as hex dump.
     *
     * @param offset
     * @param data
     * @param bytesToPrint
     * @param bytesPerRow
     * @return
     */
    public static String dump(int offset,byte[] data,int bytesToPrint, int bytesPerRow) {

        final StringBuilder buffer = new StringBuilder();
        for ( int cnt  = 0 ; cnt < bytesToPrint ; ) {
            buffer.append( hexWord(offset) ).append(": ");
            for ( int w = 0 ; w < bytesPerRow && cnt < bytesToPrint ; w++, cnt++ )
            {
                    buffer.append( hexByte( data[offset] ) );
                    offset = (offset+1) % data.length;
                    if ( (w+1) < bytesPerRow ) {
                        buffer.append(" ");
                    }

            }
            if ( (cnt+1) < bytesToPrint )
            {
                buffer.append("\n");
            }
        }
        return buffer.toString();
    }
}