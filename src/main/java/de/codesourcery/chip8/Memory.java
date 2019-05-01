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
package de.codesourcery.chip8;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class Memory
{
    private final byte[] data;

    public Memory(int sizeInBytes)
    {
        this.data = new byte[ sizeInBytes ];
    }

    public int getSizeInBytes() {
        return data.length;
    }

    public int read(int address) {
        return data[address] & 0xff;
    }

    public int load(String classpath,int address) throws IOException
    {
        InputStream in = Memory.class.getResourceAsStream( classpath );
        if (in == null)
        {
            throw new FileNotFoundException("classpath:"+classpath);
        }
        return load(in,address);
    }

    public int load(InputStream in, int address) throws IOException
    {
        Validate.notNull(in, "input stream must not be null");
        try
        {
            final byte[] input = in.readAllBytes();
            System.arraycopy( input , 0 ,data, address, input.length);
            return input.length;
        } finally {
            in.close();
        }
    }

    public void write(int address,int value) {
        this.data[address] = (byte) value;
    }

    public void write(int startAddress, byte[] data)
    {
        for ( int readPtr= 0,writePtr=startAddress,len=data.length ; readPtr < len; readPtr++,writePtr = (writePtr+1) % this.data.length )
        {
            this.data[writePtr] = data[readPtr];
        }
    }

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
