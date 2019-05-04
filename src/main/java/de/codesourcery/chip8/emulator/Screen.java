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

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The original implementation of the Chip-8 language used a 64x32-pixel monochrome display
 * with this format.
 *
 * More recently, Super Chip-48, an interpreter for the HP48 calculator, added a 128x64-pixel mode.
 * This mode is now supported by most of the interpreters on other platforms.
 *
 * Chip-8 draws graphics on screen through the use of sprites. A sprite is a group of bytes which
 * are a binary representation of the desired picture.
 * Chip-8 sprites may be up to 15 bytes, for a possible sprite size of 8x15.
 *
 * Programs may also refer to a group of sprites representing the hexadecimal digits 0 through F.
 * These sprites are 5 bytes long, or 8x5 pixels.
 * The data should be stored in the interpreter area of Chip-8 memory (0x000 to 0x1FF).
 */
public class Screen
{
    private static final int GLYPH_MEM_START = 0x000;

    public static final int WIDTH = 64;
    public static final int HEIGHT = 32;
    // Mask used to make sure out-of-range coordinates
    // get mapped back to within the screen array
    // (64*32)/8
    public static final int PIXELARRAY_LENGTH_MASK = 0xff;

    public static final int BYTES_PER_ROW = WIDTH/8;

    // enough data to hold either standard or extended mode display
    final byte[] data = new byte[ (WIDTH*HEIGHT)/8 ];
    final byte[] dataCopy1 = new byte[ (WIDTH*HEIGHT)/8 ];
    final byte[] dataCopy2 = new byte[ (WIDTH*HEIGHT)/8 ];

    private final Memory memory;
    private boolean isBeeping;
    private final AtomicBoolean hasChanged = new AtomicBoolean(true);

    private int glypPtr = GLYPH_MEM_START;

    public Screen(Memory memory) {
        this.memory = memory;
        writeGlyphs();
    }

    private void writeGlyphs()
    {
        glypPtr = GLYPH_MEM_START;
        storeGlyph(0b11110000, 0b10010000, 0b10010000, 0b10010000, 0b11110000);// "0"
        storeGlyph(0b00100000, 0b01100000, 0b00100000, 0b00100000, 0b01110000);// "1"
        storeGlyph(0b11110000, 0b00010000, 0b11110000, 0b10000000, 0b11110000);/* "2"*/
        storeGlyph(0b11110000, 0b00010000, 0b11110000, 0b00010000, 0b11110000);// "3"
        storeGlyph(0b10010000, 0b10010000, 0b11110000, 0b00010000, 0b00010000);// "4"
        storeGlyph(0b11110000, 0b10000000, 0b11110000, 0b00010000, 0b11110000);// "5"
        storeGlyph(0b11110000, 0b10000000, 0b11110000, 0b10010000, 0b11110000);// "6"
        storeGlyph(0b11110000, 0b00010000, 0b00100000, 0b01000000, 0b01000000);// "7"
        storeGlyph(0b11110000, 0b10010000, 0b11110000, 0b10010000, 0b11110000);// "8"
        storeGlyph(0b11110000, 0b10010000, 0b11110000, 0b00010000, 0b11110000);// "9"
        storeGlyph(0b11110000, 0b10010000, 0b11110000, 0b10010000, 0b10010000);// "A"
        storeGlyph(0b11100000, 0b10010000, 0b11100000, 0b10010000, 0b11100000);// "B"
        storeGlyph(0b11110000, 0b10000000, 0b10000000, 0b10000000, 0b11110000);// "C"
        storeGlyph(0b11100000, 0b10010000, 0b10010000, 0b10010000, 0b11100000);// "D"
        storeGlyph(0b11110000, 0b10000000, 0b11110000, 0b10000000, 0b11110000);// "E"
        storeGlyph(0b11110000, 0b10000000, 0b11110000, 0b10000000, 0b10000000);// "F"
    }

    private void storeGlyph(int v0,int v1,int v2,int v3,int v4)
    {
        memory.write( glypPtr++, v0 );
        memory.write( glypPtr++, v1 );
        memory.write( glypPtr++, v2 );
        memory.write( glypPtr++, v3 );
        memory.write( glypPtr++, v4 );
    }

    /**
     * Clear screen.
     */
    public void clear()
    {
        Arrays.fill(data, (byte) 0);
        hasChanged.set(true);
    }

    /**
     * Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.
     *
     * The interpreter reads n bytes from memory,
     * starting at the address stored in I.
     * These bytes are then displayed as sprites on screen at coordinates
     * (Vx, Vy).
     * Sprites are XORed onto the existing screen.
     * If this causes any pixels to be erased, VF is set to 1, otherwise it is set to 0.
     * If the sprite is positioned so part of it is outside the coordinates of the display,
     * it wraps around to the opposite side of the screen.
     * @param x
     * @param y
     * @param byteCount
     * @param spriteAddr
     * @return <code>true</code> if at least one pixel was cleared by this operation, otherwise <code>false</code>
     */
//    public boolean drawSprite(int x, int y, int byteCount, int spriteAddr)
//    {
//        System.arraycopy(data,0, dataCopy2,0,data.length);
//        boolean result1 = drawSpriteFast(x,y,byteCount,spriteAddr);
//        System.arraycopy(data,0,dataCopy1,0,data.length);
//
//        System.arraycopy(dataCopy2,0,data,0,data.length);
//        boolean result2 = drawSpriteSlow(x,y,byteCount,spriteAddr);
//        if ( result1 != result2 ) {
//            throw new RuntimeException("Pixel detection failure");
//        }
//        for ( int i = 0 ; i < data.length ; i++ ) {
//            if ( data[i] != dataCopy1[i] ) {
//                throw new RuntimeException("Pixel rendering failure");
//            }
//        }
//        return result2;
//    }

    /**
     * Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.
     *
     * The interpreter reads n bytes from memory,
     * starting at the address stored in I.
     * These bytes are then displayed as sprites on screen at coordinates
     * (Vx, Vy).
     * Sprites are XORed onto the existing screen.
     * If this causes any pixels to be erased, VF is set to 1, otherwise it is set to 0.
     * If the sprite is positioned so part of it is outside the coordinates of the display,
     * it wraps around to the opposite side of the screen.
     * @param x
     * @param y
     * @param byteCount
     * @param spriteAddr
     * @return <code>true</code> if at least one pixel was cleared by this operation, otherwise <code>false</code>
     */
    public boolean drawSpriteFast(int x, int y, int byteCount, int spriteAddr)
    {
        int clearedPixels = 0;
        int srcPtr = spriteAddr;
        int dstPtr = (x/8+y*BYTES_PER_ROW) & PIXELARRAY_LENGTH_MASK;
        int rightShift = x-((x/8)*8);
        if ( rightShift == 0)
        {
            // simple byte-copy with stride
            for ( int toCopy = byteCount ; toCopy > 0 ; toCopy--,srcPtr++)
            {
                int src = memory.read( srcPtr );
                int dst = data[ dstPtr ] & 0xff;
                int newValue = src ^ dst;
                data[dstPtr] = (byte) newValue;
                clearedPixels |= (src & (~newValue & 0xff));
                dstPtr = (dstPtr+BYTES_PER_ROW) & PIXELARRAY_LENGTH_MASK;
            }
        }
        else
        {
            int mask = 0xff<<(8-rightShift);
            int dstPtr2 = (dstPtr+1) & PIXELARRAY_LENGTH_MASK;
            for ( int toCopy = byteCount ; toCopy > 0 ; toCopy--,srcPtr++)
            {
                int src  = memory.read( srcPtr )<<(8-rightShift);
                int dst = ( (data[dstPtr] & 0xff) << 8 | (data[dstPtr2] & 0xff) );
                int newValue = (dst & ~mask) | ((src ^ dst) & mask);
                data[dstPtr] = (byte) ((newValue & 0xff00) >>>8);
                data[dstPtr2] = (byte) newValue;
                clearedPixels |= (src & (~newValue & mask));
                dstPtr = (dstPtr+BYTES_PER_ROW) & PIXELARRAY_LENGTH_MASK;
                dstPtr2 = (dstPtr+1) & PIXELARRAY_LENGTH_MASK;
            }
        }
        hasChanged.set(true);
        return clearedPixels != 0;
    }

    /**
     * Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.
     *
     * The interpreter reads n bytes from memory,
     * starting at the address stored in I.
     * These bytes are then displayed as sprites on screen at coordinates
     * (Vx, Vy).
     * Sprites are XORed onto the existing screen.
     * If this causes any pixels to be erased, VF is set to 1, otherwise it is set to 0.
     * If the sprite is positioned so part of it is outside the coordinates of the display,
     * it wraps around to the opposite side of the screen.
     * @param x
     * @param y
     * @param byteCount
     * @param spriteAddr
     * @return <code>true</code> if at least one pixel was cleared by this operation, otherwise <code>false</code>
     */
    public boolean drawSpriteSlow(int x, int y, int byteCount, int spriteAddr)
    {
        x = x % WIDTH;
        y = y % HEIGHT;
        boolean pixelsCleared = false;

        int rowStartByteOffset = x/8 + y * BYTES_PER_ROW;
        final int scrBitOffset = x -(x/8)*8;
        int scrSetMask = 0b1000_0000 >> scrBitOffset;
        int scrClearMask = ~scrSetMask;

        for ( int i = 0 ; i < byteCount ; i++ )
        {
            int spriteData = memory.read( spriteAddr+i );
            int spriteReadMask = 0b1000_0000;

            int bitOffset = scrBitOffset;
            int scrByteOffset = rowStartByteOffset;
            int screenData = this.data[scrByteOffset];
            for ( int bit = 0 ; bit < 8 ; bit++)
            {
                final boolean spriteBit = (spriteData & spriteReadMask) != 0;
                final boolean screenBit = (screenData & scrSetMask) != 0;
                final boolean newValue = spriteBit ^ screenBit;
                if ( newValue ) {
                    screenData |= scrSetMask;
                } else {
                    screenData &= scrClearMask;
                    pixelsCleared |= screenBit;
                }
                spriteReadMask >>>= 1;
                scrSetMask >>>= 1;
                scrClearMask >>= 1;
                bitOffset++;
                if ( bitOffset == 8)
                {
                    this.data[scrByteOffset] = (byte) screenData;
                    scrByteOffset = (scrByteOffset+1) & PIXELARRAY_LENGTH_MASK;
                    screenData = this.data[scrByteOffset];
                    bitOffset = 0;
                    scrSetMask    = 0b1000_0000;
                    scrClearMask  = ~scrSetMask;
                }
            }
            this.data[scrByteOffset] = (byte) screenData;
            rowStartByteOffset = (rowStartByteOffset+ BYTES_PER_ROW) & PIXELARRAY_LENGTH_MASK;
            scrSetMask = 0b1000_0000 >> scrBitOffset;
            scrClearMask = ~scrSetMask;
        }
        hasChanged.set(true);
        return pixelsCleared;
    }

    public void copyTo(BufferedImage image) {

        final byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(this.data,0,pixels,0,this.data.length);
    }

    /**
     * Get address of sprite for hexadecimal glyph <code>glypth</code>.
     * Sprite is 5 bytes high.
     * @param glyph
     * @return
     */
    public int getGlyphAddr(int glyph)
    {
        return GLYPH_MEM_START+(glyph*5);
    }

    public void setBeep(boolean onOff)
    {
        isBeeping = onOff;
    }

    public void reset()
    {
        isBeeping = false;
        clear();
        writeGlyphs();
    }

    public boolean hasChanged() {
        return hasChanged.compareAndExchange( true,false );
    }
}