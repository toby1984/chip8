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

import de.codesourcery.chip8.emulator.Memory;

import java.util.ArrayList;
import java.util.List;

/**
 * Disassembler.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Disassembler
{
    private static final StringBuffer buffer = new StringBuffer();

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final List<String> result = new ArrayList<>();

    private static void wordToHex(int value)
    {
        byteToHex( (value & 0xff00)>>>8 );
        byteToHex( value & 0xff );
    }

    private static void byteToHex(int value)
    {
        char hi = HEX[ (value & 0xf0)>>>4];
        char low = HEX[ value & 0x0f];
        buffer.append(hi).append(low);
    }

    /**
     * Disassemble's N words starting at a given address.
     *
     * This method will automatically wrap around at the end of memory.
     *
     * @param memory
     * @param startAddress
     * @param words number of words to disassembly
     * @return disassembled lines, one for each word
     */
    public static List<String> disAsm(Memory memory, int startAddress, int words)
    {
        result.clear();

        int pc = startAddress;
        for ( int i = 0 ; i < words ; i++ )
        {
            buffer.setLength( 0 );
            wordToHex( pc );
            buffer.append(": ");
            disassembleInstruction(memory,pc);
            buffer.append(" ; ");
            byteToHex( memory.read(pc) );
            byteToHex( memory.read(pc+1) );
            pc = (pc+2) % memory.getSizeInBytes();
            if ( (i+1) < words )
            {
                buffer.append("\n");
            }
            result.add( buffer.toString() );
        }
        return result;
    }

    private static void disassembleInstruction(Memory memory,final int pc)
    {
        final int cmd = memory.read( pc );
        final int data = memory.read( pc+1 );
        if ( cmd == 0x00 )
        {
            if ( data == 0xe0 )
            {
                // 0x00E0 	cls 	Clear the screen
                buffer.append("cls");
            }
            else if ( data == 0xee )
            {
                // 0x00EE 	ret return from subroutine call
                buffer.append("ret");
            } else {
                illegalInstruction();
            }
        }
        else if ( (cmd & 0xf0) == 0x10 )
        {
            // 0x1xxx 	jp xxx 	jump to address
            buffer.append("jp 0x");
            wordToHex( (cmd & 0x0f)<<8|(data& 0xff) );
        }
        else if ( (cmd & 0xf0) == 0x20 ) {
            // 0x2xxx 	jsr xxx 	jump to subroutine at address xxx 	16 levels maximum
            buffer.append("call 0x");
            wordToHex((cmd & 0x0f)<<8|(data& 0xff) );
        }
        else if ( (cmd & 0xf0) == 0x30 ) {
            // 0x3rxx 	se vr,xx 	skip if register r = constant
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            buffer.append("sse ");
            buffer.append("v"+r0).append(", 0x");
            byteToHex( cnst );
        }
        else if ( (cmd & 0xf0) == 0x40 ) {
            // 0x4rxx 	sne vr,xx 	skip if register r <> constant
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            buffer.append("sne ");
            buffer.append( "v" ).append( r0 ).append(", 0x");
            byteToHex( cnst );
        }
        else if ( (cmd & 0xf0) == 0x50 )
        {
            // 0x5ry0 	se vr,vy 	skip if register r = register y
            int r0 = cmd & 0x0f;
            int r1 = (data & 0xf0)>>>4;
            buffer.append("se v").append(r0).append(", v").append(r1);
        }
        else if ( (cmd & 0xf0) == 0x60 ) {
            // 0x6rxx 	mov vr,xx 	move constant to register r
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            buffer.append("ld v").append(r0).append(", 0x");
            byteToHex( cnst );
        }
        else if ( (cmd & 0xf0) == 0x70 ) {
            // 0x7rxx 	add vr,xx 	add constant to register r 	No carry generated
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            buffer.append("add v").append(r0).append(", 0x");
            byteToHex( cnst );
        }
        else if ( (cmd & 0xf0) == 0x80 ) {
            int dst = cmd & 0x0f;
            int src = (data & 0xf0)>>>4;
            switch( data & 0x0f ) {
                case 0x00:
                    // 0x8ry0 	mov vr,vy 	move register vy into vr
                    buffer.append("ld v").append(dst).append(",v").append(src);
                    break;
                case 0x01:
                    // 0x8ry1 	or rx,ry 	or register vy into register vr
                    buffer.append("or v").append(dst).append(",v").append(src);
                    break;
                case 0x02:
                    // 0x8ry2 	and rx,ry 	and register vy into register vx
                    buffer.append("and v").append(dst).append(",v").append(src);
                    break;
                case 0x03:
                    // 0x8ry3 	xor rx,ry 	exclusive or register ry into register rx
                    buffer.append("xor v").append(dst).append(",v").append(src);
                    break;
                case 0x04:
                    // 0x8ry4 	add vr,vy 	add register vy to vr,carry in vf
                    buffer.append("add v").append(dst).append(",v").append(src);
                    break;
                case 0x05:
                    // 0x8ry5 	sub vr,vy 	subtract register vy from vr,borrow in vf
                    // vf set to 1 if borrows
                    buffer.append("sub v").append(dst).append(",v").append(src);
                    break;
                case 0x06:
                    // 0x8xy6 	shr vr 	shift register vy right 1 bit and copy it to vx, bit 0 goes into register vf
                    buffer.append("shr v").append(dst).append(",v").append(src);
                    break;
                case 0x07:
                    /*
                     * 8xy7 - SUBN Vx, Vy
                     * Set Vx = Vy - Vx, set VF = NOT borrow.
                     * If Vy > Vx, then VF is set to 1, otherwise 0.
                     * Then Vx is subtracted from Vy, and the results stored in Vx.
                     */
                    buffer.append("subn v").append(src).append(",v").append(dst);
                    break;
                case 0x0e:
                    // 0x8xye 	shl vx,vy 	shift vy left 1 bit and copy it to vx,bit 7 goes into register vf
                    buffer.append("shl v").append(dst).append(",v").append(src);
                    break;
                default:
                    throw new RuntimeException("Unhandled opcode: 0x"+Integer.toHexString(cmd<<8|data) );
            }
        }
        else if ( (cmd & 0xf0) == 0x90 ) {
            // 0x9ry0 	sne rx,ry 	skip if register rx <> register ry
            int r0 = cmd & 0x0f;
            int r1 = (data & 0xf0)>>>4;
            buffer.append("sne v").append(r0).append(",v").append(r1);
        }
        else if ( (cmd & 0xf0) == 0xa0 ) {
            // 0xaxxx 	mvi xxx 	Load index register with constant xxx
            int index = (cmd & 0x0f)<<8 | (data & 0xff);
            buffer.append("ld I, ");
            wordToHex( index );
        }
        else if ( (cmd & 0xf0) == 0xb0 ) {
            // 0xbxxx 	jmi xxx 	Jump to address xxx+register v0
            int adr = (cmd & 0x0f)<<8 | (data & 0xff);
            buffer.append("jp v0, ");
            wordToHex( adr );
        }
        else if ( (cmd & 0xf0) == 0xc0 ) {
            // 0xcrxx 	rnd vr,xxx    	vr = random number less than or equal to xxx
            buffer.append("rnd v").append(cmd&0x0f).append(",");
            byteToHex(data&0xff);
        }
        else if ( (cmd & 0xf0) == 0xd0 )
        {
            int x = cmd & 0x0f;
            int y = (data & 0xf0)>>>4;
            int height = (data & 0x0f);
            buffer.append("drw ").append(x).append(",")
                    .append(y).append(",").append(height);
        }
        else if ( (cmd & 0xf0) == 0xe0 )
        {
            int key = cmd & 0x0f;
            if ( data == 0x9e )
            {
                // 0xek9e 	skp k 	skip if key (register rk) pressed
                // The key is a key number, see the chip-8 documentation
                buffer.append("skp ").append( key );
            }
            else if ( data == 0xa1 )
            {
                // 0xeka1 	sknp k 	skip if key (register rk) not pressed
                buffer.append("sknp ").append( key );
            } else {
                illegalInstruction();
            }
        }
        else if ( (cmd & 0xf0) == 0xf0 )
        {
            int r0 = (cmd & 0x0f);
            switch( data )
            {
                case 0x07:   // 0xfr07	gdelay vr 	get delay timer into vr
                    buffer.append("ld v").append(r0).append(",DT");
                    break;
                case 0x0a:   // 0xfr0a	key vr wait for keypress,put key in register vr
                    buffer.append("ld v").append(r0).append(",K");
                    break;
                case 0x15:   // 0xfr15	sdelay vr 	set the delay timer to vr
                    buffer.append("ld dt, v").append(r0);
                    break;
                case 0x18:   // 0xfr18	ssound vr 	set the sound timer to vr
                    buffer.append("ld st, v").append(r0);
                    break;
                case 0x1e:   // 0xfr1e	adi vr add register vr to the index register
                    buffer.append("add i, v").append(r0);
                    break;
                case 0x29:   // 0xfr29	font vr 	point I to the sprite for hexadecimal character in vr
                    // Sprite is 5 bytes high
                    buffer.append("ld v").append(r0).append(", F");
                    break;
                case 0x33:   // 0xfr33	bcd vr 	store the bcd representation of register vr at
                    // // location I,I+1,I+2
                    // Doesn't change I
                    buffer.append("ld B, v").append(r0);
                    break;
                case 0x55:   // 0xfr55	str v0-vr 	store registers v0-vr at location I onwards
                    // I is incremented to point to the next location on. e.g. I = I + r + 1
                    buffer.append("ld [I], v").append(r0);
                    break;
                case 0x65:   // 0xfx65	ldr v0-vr 	load registers v0-vr from location I onwards as above.
                    buffer.append("ld v").append(r0).append(",[I]");
                    break;
                default:
                    illegalInstruction();
            }
        } else {
            illegalInstruction();
        }
    }

    private static void illegalInstruction()
    {
        buffer.append("illegal");
    }
}