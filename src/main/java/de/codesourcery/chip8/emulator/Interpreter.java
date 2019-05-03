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

import de.codesourcery.chip8.Disassembler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class Interpreter
{
    private static final boolean DEBUG = false;
    private static final boolean TRACE = false;
    private static final boolean TRACE_KEYBOARD = false;

    private static final boolean CAPTURE_BACKTRACE = false;
    private static final int BACKTRACE_SIZE = 16;

    private static final int FLAG_WAIT_DELAY = 1;
    private static final int FLAG_WAIT_KEY_PRESS = 2;
    private static final int FLAG_WAIT_KEY_RELEASE = 4;

    public final Memory memory;
    public final Screen screen;
    public final Keyboard keyboard;

    public final Timer soundTimer;
    public final Timer delayTimer;

    public int pc = 0x200;
    public int sp;
    public int index;
    public int register[] = new int[16];
    public int stack[] = new int[16];

    private final int[] backtrace = new int[BACKTRACE_SIZE];
    private int backtraceReadPtr = 0;
    private int backtraceWritePtr = 0;

    private int waitFlags;

    // keyboard handling
    private int pressedKey;
    private int keyDestReg;

    private static final int RAND_SEED = 0xdeadbeef;

    private final Random rnd = new Random( RAND_SEED );

    private volatile Consumer<Interpreter> resetHook;

    public Interpreter(Memory memory, Screen screen, Keyboard keyboard, Timer soundTimer, Timer delayTimer, Consumer<Interpreter> resetHook) {
        this.memory = memory;
        this.screen = screen;
        this.keyboard = keyboard;
        this.soundTimer = soundTimer;
        this.delayTimer = delayTimer;
        this.resetHook = resetHook;
        reset();
    }

    public void setResetHook(Consumer<Interpreter> resetHook)
    {
        Validate.notNull(resetHook, "resetHook must not be null");
        this.resetHook = resetHook;
    }

    public void soundTimerTriggered() {
        screen.setBeep(false);
    }

    public void delayTimerTriggered()
    {
        waitFlags &= ~FLAG_WAIT_DELAY;
    }

    public void reset()
    {
        soundTimer.reset();
        delayTimer.reset();

        rnd.setSeed( RAND_SEED );
        // memory must be reset BEFORE screen
        // as screen is going to store glyph data in memory
        memory.reset();
        screen.reset();
        keyboard.reset();

        keyDestReg = 0;

        Arrays.fill(stack,0);
        Arrays.fill(register,0);

        pc = 0x200;
        sp = 0;
        index = 0;
        waitFlags = 0;

        resetHook.accept( this );
    }

    private static void debug(String msg)
    {
        if ( DEBUG )
        {
            System.out.println( "DEBUG: " + msg );
        }
    }

    private static void trace(String msg) {
        if ( TRACE )
        {
            System.out.println( "trace: " + msg );
        }
    }

    private static void traceKeyboard(String msg)
    {
        if ( TRACE_KEYBOARD)
        {
            System.out.println( "trace: " + msg );
        }
    }

    public void tick()
    {
        if ( waitFlags == 0 )
        {
            if ( CAPTURE_BACKTRACE ) {
                backtrace[ backtraceWritePtr ] = pc;
                backtraceWritePtr++;
                if ( backtraceWritePtr == BACKTRACE_SIZE ) {
                    backtraceWritePtr = 0;
                    backtraceReadPtr = (backtraceReadPtr+1) % BACKTRACE_SIZE;
                }
            }

            executeInstruction();
        }
    }

    public void keyPressed(int key) {
        if ( ( waitFlags & FLAG_WAIT_KEY_PRESS) != 0 )
        {
            pressedKey = key;
            waitFlags &= ~FLAG_WAIT_KEY_PRESS;
            waitFlags |= FLAG_WAIT_KEY_RELEASE;
        }
    }

    public void keyReleased(int key)
    {
        if ( ( waitFlags & FLAG_WAIT_KEY_RELEASE) != 0 && key == pressedKey )
        {
            register[ keyDestReg ] = key;
            waitFlags &= ~FLAG_WAIT_KEY_RELEASE;
        }
    }

    private void executeInstruction()
    {
        if ( DEBUG )
        {
            final List<String> lines = Disassembler.disAsm( memory, pc, 1 );
            for (int i = 0, linesSize = lines.size(); i < linesSize; i++)
            {
                debug(lines.get( i ));
            }
        }

        final int cmd = memory.read( pc++ );
        final int data = memory.read( pc++ );
        if ( cmd == 0x00 )
        {
            if ( data == 0xe0 )
            {
                // 0x00E0 	cls 	Clear the screen
                screen.clear();
            }
            else if ( data == 0xee )
            {
                // 0x00EE 	rts 	return from subroutine call
                pc = stack[ --sp ];
            }
            else
            {
                illegalInstruction( cmd, data );
            }
            return;
        }
        switch (cmd & 0xf0)
        {
            case 0x10:
                // 0x1xxx 	jmp xxx 	jump to address
                pc = (cmd & 0x0f) << 8 | (data & 0xff);
                break;
            case 0x20:
                // 0x2xxx 	jsr xxx 	jump to subroutine at address xxx 	16 levels maximum
                stack[sp++] = pc;
                pc = (cmd & 0x0f) << 8 | (data & 0xff);
                break;
            case 0x30:
            {
                // 0x3rxx 	skeq vr,xx 	skip if register r = constant
                int r0 = cmd & 0x0f;
                int cnst = data & 0xff;
                final boolean skip = register[r0] == cnst;

                if (TRACE)
                    trace("SKIP: " + skip + " (0x" + Integer.toHexString(register[r0]) + " == 0x" + Integer.toHexString(cnst));
                if (skip)
                {
                    pc += 2;
                }
                break;
            }
            case 0x40:
            {
                // 0x4rxx 	skne vr,xx 	skip if register r <> constant
                int r0 = cmd & 0x0f;
                int cnst = data & 0xff;
                final boolean skip = register[r0] != cnst;
                if (TRACE)
                    trace("SKIP: " + skip + " (0x" + Integer.toHexString(register[r0]) + " <> 0x" + Integer.toHexString(cnst));
                if (skip)
                {
                    pc += 2;
                }
                break;
            }
            case 0x50:
            {
                // 0x5ry0 	skeq vr,vy 	skip if register r = register y
                int r0 = cmd & 0x0f;
                int r1 = (data & 0xf0) >>> 4;
                final boolean skip = register[r0] == register[r1];
                if (TRACE)
                    trace("SKIP: " + skip + " (0x" + Integer.toHexString(register[r0]) + " <> 0x" + Integer.toHexString(register[r1]));
                if (skip)
                {
                    pc += 2;
                }
                break;
            }
            case 0x60:
            {
                // 0x6rxx 	mov vr,xx 	move constant to register r
                int r0 = cmd & 0x0f;
                int cnst = data & 0xff;
                register[r0] = cnst;
                if (TRACE) trace("Register " + r0 + " = 0x" + Integer.toHexString(cnst));
                break;
            }
            case 0x70:
            {
                // 0x7rxx 	add vr,vx 	add constant to register r 	: no carry is generated
                int r0 = cmd & 0x0f;
                int cnst = data & 0xff;
                register[r0] = (register[r0] + cnst) & 0xff;
                if (TRACE) trace("Register " + r0 + " = 0x" + Integer.toHexString(register[r0]));
                break;
            }
            case 0x80:
                int dst = cmd & 0x0f;
                int src = (data & 0xf0) >>> 4;
                switch (data & 0x0f)
                {
                    case 0x00:
                        // 0x8ry0 	mov vr,vy 	move register vy into vr
                        register[dst] = register[src];
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        break;
                    case 0x01:
                        // 0x8ry1 	or rx,ry 	or register vy into register vr
                        register[dst] |= register[src];
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        break;
                    case 0x02:
                        // 0x8ry2 	and rx,ry 	and register vy into register vx
                        register[dst] &= register[src];
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        break;
                    case 0x03:
                        // 0x8ry3 	xor rx,ry 	exclusive or register ry into register rx
                        register[dst] = (register[dst] ^ register[src]) & 0xff;
                        trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        break;
                    case 0x04:
                        // 0x8ry4 	add vr,vy 	add register vy to vr,carry in vf
                        int tmp = register[dst] + register[src];
                        register[0x0f] = (tmp & 0xffffff00) != 0 ? 1 : 0;
                        register[dst] = (tmp & 0xff);
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        if (TRACE) trace("VF = " + register[0x0f]);
                        break;
                    case 0x05:
                        /*
                         * 8xy5 - SUB Vx, Vy
                         * Set Vx = Vx - Vy, set VF = NOT borrow.
                         * If Vy > Vx, then VF is set to 0, otherwise 1.
                         * Then Vy is subtracted from Vx, and the results stored in Vx.
                         */
                        // 0x8ry5 	sub vr,vy 	subtract register vy from vr,borrow in vf
                        // vf set to 0 if a borrow occurs
                        register[0x0f] = register[src] > register[dst] ? 0 : 1;
                        register[dst] = (register[dst] - register[src]) & 0xff;
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        if (TRACE) trace("VF = " + register[0x0f]);
                        break;
                    case 0x06:
                        // 8XY6
                        // Shift register VY right one bit and copy it to register VX
                        //Set register VF to the least significant bit prior to the shift
                        register[0x0f] = register[src] & 1;
                        register[src] >>>= 1;
                        register[dst] = register[src];
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        break;
                    case 0x07:
                        /*
                         * 8xy7 - SUBN Vx, Vy
                         * Set Vx = Vy - Vx, set VF = NOT borrow.
                         * If Vy > Vx, then VF is set to 1, otherwise 0.
                         * Then Vx is subtracted from Vy, and the results stored in Vx.
                         */
                        register[0x0f] = register[dst] > register[src] ? 0 : 1;
                        register[dst] = (register[src] - register[dst]) & 0xff;
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        if (TRACE) trace("VF = " + register[0x0f]);
                        break;
                    case 0x0e:
                        // 0x8r0e 	shl vr 	shift register vr left,bit 7 goes into register vf
                        register[0x0f] = (register[src] & 0b1000_0000) >>> 7;
                        register[src] = (register[src] << 1) & 0xff;
                        register[dst] = register[src];
                        if (TRACE) trace("Register " + dst + " = 0x" + Integer.toHexString(register[dst]));
                        if (TRACE) trace("VF = " + register[0x0f]);
                        break;
                    default:
                        throw new RuntimeException("Unhandled opcode: 0x" + Integer.toHexString(cmd << 8 | data));
                }
                break;
            case 0x90:
            {
                // 0x9ry0 	skne rx,ry 	skip if register rx <> register ry
                int r0 = cmd & 0x0f;
                int r1 = (data & 0xf0) >>> 4;
                final boolean skip = register[r0] != register[r1];
                if (TRACE)
                    trace("skip: " + skip + " ( " + Integer.toHexString(register[r0]) + " <> " + Integer.toHexString(register[r1]));
                if (skip)
                {
                    pc += 2;
                }
                break;
            }
            case 0xa0:
                // 0xaxxx 	mvi xxx 	Load index register with constant xxx
                index = (cmd & 0x0f) << 8 | (data & 0xff);
                if (TRACE) trace("Index: 0x" + Integer.toHexString(index));
                break;
            case 0xb0:
                // 0xbxxx 	jmi xxx 	Jump to address xxx+register v0
                int adr = register[0x00] + (cmd & 0x0f) << 8 | (data & 0xff);
                pc = (adr & 0xfff);
                break;
            case 0xc0:
            {
                // 0xcrxx 	rand vr,xxx    	vr = random number less than or equal to xxx
                final int reg = cmd & 0x0f;
                final int cnst = data & 0xff;
                register[reg] = rnd.nextInt(256) & cnst;
                break;
            }
            case 0xd0:
                int x = register[cmd & 0x0f];
                int y = register[(data & 0xf0) >>> 4];
                int height = (data & 0x0f);
                if (TRACE)
                    trace("drawSprite @ 0x" + Integer.toHexString(index) + ": x=" + x + ",y=" + y + ",h=" + height);
                register[0x0f] = screen.drawSprite(x, y, height, index) ? 1 : 0;
                break;
            case 0xe0:
                int key = register[cmd & 0x0f];
                if (data == 0x9e)
                {
                    // 0xek9e 	skpr k 	skip if key (register rk) pressed
                    // The key is a key number, see the chip-8 documentation
                    final boolean pressed = keyboard.isKeyPressed(key);
                    if (TRACE) trace("keyPressed( " + key + ") => " + pressed);
                    if (pressed)
                    {
                        pc += 2;
                    }
                }
                else if (data == 0xa1)
                {
                    // 0xeka1 	skup k 	skip if key (register rk) not pressed
                    final boolean notPressed = !keyboard.isKeyPressed(key);
                    if (TRACE) trace("keyNotPressed( " + key + ") => " + notPressed);
                    if (notPressed)
                    {
                        pc += 2;
                    }
                }
                else
                {
                    illegalInstruction(cmd, data);
                }
                break;
            case 0xf0:
            {
                int r0 = (cmd & 0x0f);
                switch (data)
                {
                    case 0x07:   // 0xfr07	gdelay vr 	get delay timer into vr
                        register[r0] = delayTimer.value() & 0xff;
                        if (TRACE) trace("Register " + r0 + " = " + Integer.toHexString(register[r0]));
                        break;
                    case 0x0a:   // 0xfr0a	key vr wait for keypress,put key in register vr
                        if (TRACE_KEYBOARD) traceKeyboard("Waiting for keypress");
                        keyDestReg = r0;
                        waitFlags |= FLAG_WAIT_KEY_PRESS;
                        break;
                    case 0x15:   // 0xfr15	sdelay vr 	set the delay timer to vr
                        if (TRACE) trace("delay-timer = " + Integer.toHexString(register[r0]));
                        if (register[r0] > 0)
                        {
                            waitFlags |= FLAG_WAIT_DELAY;
                        }
                        delayTimer.setValue(register[r0]);
                        break;
                    case 0x18:   // 0xfr18	ssound vr 	set the sound timer to vr
                        soundTimer.setValue(register[r0]);
                        if (TRACE) trace("sound-timer = " + Integer.toHexString(register[r0]));
                        screen.setBeep(register[r0] > 0);
                        break;
                    case 0x1e:   // 0xfr1e	adi vr add register vr to the index register
                        index = (index + register[r0]) & 0xfff;
                        if (TRACE) trace("Index = 0x" + Integer.toHexString(index));
                        break;
                    case 0x29:   // 0xfr29	font vr 	point I to the sprite for hexadecimal character in vr
                        // Sprite is 5 bytes high
                        index = screen.getGlyphAddr(register[r0]);
                        if (TRACE) trace("Index = 0x" + Integer.toHexString(index));
                        break;
                    case 0x33:   // 0xfr33	bcd vr 	store the bcd representation of register vr at
                        // // location I,I+1,I+2
                        // Doesn't change I
                        String value = Integer.toString(register[r0]);
                        value = StringUtils.leftPad(value, 3, '0');

                        int ptr = index;
                        if (TRACE) trace("Writing '" + value + "' @ 0x" + Integer.toHexString(ptr));
                        memory.write(ptr, value.charAt(0));
                        ptr = (ptr + 1) & 0xfff;
                        memory.write(ptr, value.charAt(1));
                        ptr = (ptr + 1) & 0xfff;
                        memory.write(ptr, value.charAt(2));
                        break;
                    case 0x55:   // 0xfr55	str v0-vr 	store registers v0-vr at location I onwards
                        // I is incremented to point to the next location on. e.g. I = I + r + 1
                        ptr = index;
                        if (TRACE) trace("Storing registers r0-r" + r0 + "' @ 0x" + Integer.toHexString(ptr));
                        for (int i = 0; i <= r0; )
                        {
                            memory.write(ptr, register[i++]);
                            ptr = (ptr + 1) & 0xfff;
                        }
                        index = ptr;
                        break;
                    case 0x65:   // 0xfx65	ldr v0-vr 	load registers v0-vr from location I onwards as above.
                        ptr = index;
                        if (TRACE) trace("Loading registers r0-r" + r0 + "' from 0x" + Integer.toHexString(ptr));
                        for (int i = 0; i <= r0; )
                        {
                            register[i++] = memory.read(ptr);
                            ptr = (ptr + 1) & 0xfff;
                        }
                        index = ptr;
                        break;
                    default:
                        illegalInstruction(cmd, data);
                }
                break;
            }
            default:
                illegalInstruction(cmd, data);
                break;
        }
    }

    private void illegalInstruction(int cmd, int data)
    {
        if ( CAPTURE_BACKTRACE ) {

            final List<Integer> trace = new ArrayList<>();
            for ( int i = backtraceReadPtr ; i != backtraceWritePtr ; i = (i+1) % BACKTRACE_SIZE ) {
                trace.add( backtrace[i] );
            }
            Collections.reverse(trace);
            System.err.println("Unhandled opcode: 0x"+Integer.toHexString(cmd<<8|data));
            System.err.println("\nBacktrace:\n");
            for ( int adr : trace ) {
                System.out.println("PC = 0x"+Integer.toHexString(adr ) );
            }
        }
        throw new RuntimeException("Unhandled opcode: 0x"+Integer.toHexString(cmd<<8|data)+" at address 0x"+Integer.toHexString(pc-2));
    }
}