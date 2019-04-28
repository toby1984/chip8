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

import java.util.Arrays;

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

    public enum Mode
    {
        DEFAULT {
            @Override public int width() { return 64; }
            @Override public int height() { return 32; }
        },
        EXTENDED {
            @Override public int width() { return 128; }
            @Override public int height() { return 64; }
        };
        public abstract int width();
        public abstract int height();
    }

    // enough data to hold either standard or extended mode display
    private final byte[] data = new byte[ (128*64)/8 ];

    private final Memory memory;
    private Mode mode = Mode.DEFAULT;
    private boolean isBeeping;

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

    public Mode getMode()
    {
        return mode;
    }

    public byte[] getData() {
        return data;
    }

    public int getBytesPerRow() {
        return mode.width()/8;
    }

    /**
     * Scroll down X lines (Super only).
     *
     * @param lines
     */
    public void scrollDown(int lines)
    {
        int toScroll = lines % mode.height();
        for ( int i =0 ; i < toScroll ; i++ ) {
            scrollDown();
        }
    }

    private void scrollDown() {

        final int bytesPerRow = getBytesPerRow();
        int lastRowOffset = (mode.height()-1)*(mode.width()/8);
        int previousRowOffset = (mode.height()-2)*(mode.width()/8);
        for ( int rows = mode.height(); rows > 0 ; rows--)
        {
            for ( int i = 0 ; i < bytesPerRow ; i++)
            {
                data[ lastRowOffset + i ] = data[ previousRowOffset + i ];
            }
            lastRowOffset -= bytesPerRow;
            previousRowOffset -= bytesPerRow;
        }
    }

    /**
     * Clear screen.
     */
    public void clear()
    {
        Arrays.fill(data, (byte) 0);
    }

    /**
     * Scroll left 4 pixels (Super only).
     */
    public void scrollLeft()
    {
        // TODO: Implement me
        throw new RuntimeException("scrollLeft() not implemented");
    }

    /**
     * Scroll right 4 pixels (Super only).
     */
    public void scrollRight()
    {
        // TODO: Implement me
        throw new RuntimeException("scrollRight() not implemented");
    }

    /**
     * Enable extended screen mode (128 x 64) (Super only).
     *
     * @param enabled
     */
    public void setExtendedMode(boolean enabled)
    {
        this.mode = enabled ? Mode.EXTENDED : Mode.DEFAULT;
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
    public boolean drawSprite(int x, int y, int byteCount, int spriteAddr)
    {
        boolean pixelsCleared = false;
        int currentY = y;
        for ( int i = 0 ; i < byteCount ; i++ )
        {
            int data = memory.read( spriteAddr+i );
            int mask = 0b1000_0000;
            for ( int currentX = x, bit = 0 ; bit < 8 ;
                  bit++,
                  mask >>>= 1,
                  currentX = (currentX+1) % mode.width() )
            {
                final boolean spriteBit = (data & mask)!=0;
                final boolean screenBit = readPixel(currentX,currentY);
                final boolean newValue = spriteBit ^ screenBit;
                if ( newValue ) {
                    setPixel( currentX, currentY );
                } else {
                    clearPixel( currentX, currentY );
                    pixelsCleared |= screenBit;
                }
            }
            currentY = (currentY+1) % mode.height();
        }
        return pixelsCleared;
    }

    public boolean readPixel(int x,int y) {
        int byteOffset = getBytesPerRow()*y + (x/8);
        int bitOffset = x - (x/8);
        int mask = 0b1000_0000 >>> bitOffset;
        return (data[ byteOffset ] & mask) != 0;
    }

    private void setPixel(int x,int y) {
        int byteOffset = getBytesPerRow()*y + (x/8);
        int bitOffset = x - (x/8);
        int mask = 0b1000_0000 >>> bitOffset;
        data[ byteOffset ] |= mask;
    }

    private void clearPixel(int x,int y) {
        int byteOffset = getBytesPerRow()*y + (x/8);
        int bitOffset = x - (x/8);
        int mask = 0b1000_0000 >>> bitOffset;
        data[ byteOffset ] &= ~mask;
    }

    /**
     * Draw extended sprite (16x16) at screen location rx,ry (Super only).
     *
     * Sprites stored in memory at location in index register, maximum 8 bits wide.
     * Wraps around the screen. If when drawn, clears a pixel, vf is set to 1
     * otherwise it is zero. All drawing is xor drawing (e.g. it toggles the screen pixels)
     *
     * @param x
     * @param y
     * @param spriteAddr
     * @return <code>true</code> if at least one pixel was cleared by this operation, otherwise <code>false</code>
     */
    public boolean drawExtendedSprite(int x, int y,int spriteAddr)
    {
        // TODO: Implement me
        throw new RuntimeException("drawExtendedSprite() not implemented");
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

    /**
     * Get address of sprite for hexadecimal glyph <code>glypth</code> (Super only).
     * Sprite is 10 bytes high.
     * @param glyph
     * @return
     */
    public int getGlyphAddrExt(int glyph)
    {
        // TODO: Implement me
        throw new RuntimeException("getGlyphAddrExt() not implemented");
    }

    public void setBeep(boolean onOff)
    {
        isBeeping = onOff;
    }

    public void reset()
    {
        isBeeping = false;
        Arrays.fill( data, (byte) 0);
        writeGlyphs();
    }
}