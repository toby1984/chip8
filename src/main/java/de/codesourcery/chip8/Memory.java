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

    public void load(String classpath,int address) throws IOException
    {
        InputStream in = Memory.class.getResourceAsStream( classpath );
        if ( in != null ) {
            load(in,address);
        } else {
            throw new FileNotFoundException("classpath:"+classpath);
        }
    }

    public void load(InputStream in, int address) throws IOException
    {
        try
        {
            final byte[] input = in.readAllBytes();
            System.arraycopy( input , 0 ,data, address, input.length);
        } finally {
            in.close();
        }
    }

    public void write(int address,int value) {
        this.data[address] = (byte) value;
    }

    public void reset()
    {
        Arrays.fill(data,(byte) 0);
    }
}
