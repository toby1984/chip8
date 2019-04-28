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
import java.util.Random;
import java.util.function.Consumer;

public class Interpreter
{
    private static final boolean DEBUG = false;

    public final Memory memory;
    public final Screen screen;
    public final Keyboard keyboard;

    private final SixtyHertzTimer timer=new SixtyHertzTimer();

    private final Timer soundTimer = new Timer( "sound" )
    {
        @Override
        protected void triggered()
        {
            screen.setBeep( false );
        }
    };
    private final Timer delayTimer = new Timer( "delay" )
    {
        @Override
        protected void triggered()
        {
            waitForDelay = false;
        }
    };

    private int pc = 0x200;
    private int sp;
    private int index;
    private int register[] = new int[16];
    private int stack[] = new int[16];

    // keyboard handling
    private boolean waitForKey;
    private int keyDestReg;
    private boolean waitForDelay;

    private static final int RAND_SEED = 0xdeadbeef;

    private final Random rnd = new Random( RAND_SEED );

    private final Consumer<Interpreter> resetHook;

    public Interpreter(Memory memory, Screen screen, Keyboard keyboard,
                       Consumer<Interpreter> resetHook) {
        this.memory = memory;
        this.screen = screen;
        this.keyboard = keyboard;
        this.resetHook = resetHook;
        resetHook.accept( this );
        timer.addListener( soundTimer );
        timer.addListener( delayTimer );
        timer.start();
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

        waitForKey = false;
        keyDestReg = 0;
        waitForDelay = false;

        Arrays.fill(stack,0);
        Arrays.fill(register,0);

        pc = 0x200;
        sp = 0;
        index = 0;
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
        if ( DEBUG )
        {
            System.out.println( "trace: " + msg );
        }
    }

    public void tick()
    {
        if ( waitForDelay ) {
            trace("Waiting for delay");
            return;
        }
        if ( waitForKey )
        {
            final int pressed = keyboard.readKey();
            if ( pressed == Keyboard.NO_KEY )
            {
                trace("Waiting for keypress");
                return;
            }
            waitForKey = false;
            register[ keyDestReg ] = pressed;
            debug("Got key "+pressed);
        }
        executeInstruction();
    }

    private void executeInstruction()
    {
        if ( DEBUG )
        {
            debug( Disassembler.disAsm( memory, pc, 1 ) );
        }

        final int cmd = memory.read( pc++ );
        final int data = memory.read( pc++ );
        if ( cmd == 0x00 )
        {
            if ( (data & 0xf0) == 0xc0 )
            {
                // 0x00Cx 	scdown x 	Scroll the screen down x lines 	Super only, not implemented
                screen.scrollDown( data & 0x0f );
            }
            else if ( data == 0xe0 )
            {
                // 0x00E0 	cls 	Clear the screen
                screen.clear();
            }
            else if ( data == 0xee )
            {
                // 0x00EE 	rts 	return from subroutine call
                pc = stack[ --sp ];
            }
            else if ( data == 0xfb )
            {
                // 0x00FB 	scright 	scroll screen 4 pixels right 	Super only,not implemented
                screen.scrollRight();
            }
            else if ( data == 0xfc )
            {
                // 0x00FC 	scleft 	scroll screen 4 pixels left 	Super only,not implemented
                screen.scrollLeft();
            }
            else if ( data == 0xfe )
            {
                // 0x00FE 	low 	disable extended screen mode 	Super only
                screen.setExtendedMode( false );
            }
            else if ( data == 0xff )
            {
                // 0x00FF 	high 	enable extended screen mode (128 x 64) 	Super only
                screen.setExtendedMode( true );
            }
            else
            {
                illegalInstruction( cmd, data );
            }
        }
        else if ( (cmd & 0xf0) == 0x10 )
        {
            // 0x1xxx 	jmp xxx 	jump to address
            pc = (cmd & 0x0f)<<8|(data& 0xff);
        }
        else if ( (cmd & 0xf0) == 0x20 ) {
            // 0x2xxx 	jsr xxx 	jump to subroutine at address xxx 	16 levels maximum
            stack[ sp++ ] = pc;
            pc = (cmd & 0x0f)<<8|(data& 0xff);
        }
        else if ( (cmd & 0xf0) == 0x30 ) {
            // 0x3rxx 	skeq vr,xx 	skip if register r = constant
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            final boolean skip = register[r0] == cnst;
            trace("SKIP: "+skip+" (0x"+Integer.toHexString( register[r0])+" == 0x"+Integer.toHexString(cnst));
            if ( skip ) {
                pc += 2;
            }
        }
        else if ( (cmd & 0xf0) == 0x40 ) {
            // 0x4rxx 	skne vr,xx 	skip if register r <> constant
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            final boolean skip = register[r0] != cnst;
            trace("SKIP: "+skip+" (0x"+Integer.toHexString( register[r0])+" <> 0x"+Integer.toHexString(cnst));
            if ( skip ) {
                pc += 2;
            }
        }
        else if ( (cmd & 0xf0) == 0x50 )
        {
            // 0x5ry0 	skeq vr,vy 	skip if register r = register y
            int r0 = cmd & 0x0f;
            int r1 = (data & 0xf0)>>>4;
            final boolean skip = register[r0] == register[r1];
            trace("SKIP: "+skip+" (0x"+Integer.toHexString( register[r0])+" <> 0x"+Integer.toHexString(register[r1]));
            if ( skip ) {
                pc += 2;
            }
        }
        else if ( (cmd & 0xf0) == 0x60 ) {
            // 0x6rxx 	mov vr,xx 	move constant to register r
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            register[r0] = cnst;
            trace("Register "+r0+" = 0x"+Integer.toHexString( cnst ) );
        }
        else if ( (cmd & 0xf0) == 0x70 ) {
            // 0x7rxx 	add vr,vx 	add constant to register r 	No carry generated
            int r0 = cmd & 0x0f;
            int cnst = data & 0xff;
            register[r0] = (register[r0]+cnst) & 0xff;
            trace("Register "+r0+" = 0x"+Integer.toHexString( register[r0] ) );
        }
        else if ( (cmd & 0xf0) == 0x80 ) {
            int dst = cmd & 0x0f;
            int src = (data & 0xf0)>>>4;
            switch( data & 0x0f ) {
                case 0x00:
                    // 0x8ry0 	mov vr,vy 	move register vy into vr
                    register[dst] = register[src];
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    break;
                case 0x01:
                    // 0x8ry1 	or rx,ry 	or register vy into register vr
                    register[dst] |= register[src];
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    break;
                case 0x02:
                    // 0x8ry2 	and rx,ry 	and register vy into register vx
                    register[dst] &= register[src];
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    break;
                case 0x03:
            // 0x8ry3 	xor rx,ry 	exclusive or register ry into register rx
                    register[dst] = (register[dst] ^ register[src]) & 0xff;
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    break;
                case 0x04:
            // 0x8ry4 	add vr,vy 	add register vy to vr,carry in vf
                    int tmp = register[dst] + register[src];
                    register[0x0f] = (tmp & 0xffffff00) != 0 ? 1 : 0;
                    register[dst] = (tmp & 0xff);
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    trace("VF = "+register[0x0f]);
                    break;
                case 0x05:
                    /*
                     * 8xy5 - SUB Vx, Vy
                     * Set Vx = Vx - Vy, set VF = NOT borrow.
                     * If Vx > Vy, then VF is set to 1, otherwise 0.
                     * Then Vy is subtracted from Vx, and the results stored in Vx.
                     */
                    // 0x8ry5 	sub vr,vy 	subtract register vy from vr,borrow in vf
                    // vf set to 1 if borrows
                    register[0x0f] = register[dst] > register[src] ? 1 : 0;
                    register[dst] = (register[dst] - register[src] ) & 0xff;
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    trace("VF = "+register[0x0f]);
                    break;
                case 0x06:
                    // 0x8r06 	shr vr 	shift register vy right, bit 0 goes into register vf
                    register[0x0f] = register[dst] & 1;
                    register[dst] >>>= 1;
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    break;
                case 0x07:
                    /*
                     * 8xy7 - SUBN Vx, Vy
                     * Set Vx = Vy - Vx, set VF = NOT borrow.
                     * If Vy > Vx, then VF is set to 1, otherwise 0.
                     * Then Vx is subtracted from Vy, and the results stored in Vx.
                     */
                    register[0x0f] = register[src] > register[dst] ? 1 : 0;
                    register[dst] = (register[src] - register[dst] ) & 0xff;
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    trace("VF = "+register[0x0f]);
                    break;
                case 0x0e:
                    // 0x8r0e 	shl vr 	shift register vr left,bit 7 goes into register vf
                    register[0x0f] = (register[dst] & 0b1000_0000) >>> 7;
                    register[dst] = (register[dst] << 1) & 0xff;
                    trace("Register "+dst+" = 0x"+Integer.toHexString( register[dst] ) );
                    trace("VF = "+register[0x0f]);
                    break;
                default:
                    throw new RuntimeException("Unhandled opcode: 0x"+Integer.toHexString(cmd<<8|data) );
            }
        }
        else if ( (cmd & 0xf0) == 0x90 ) {
            // 0x9ry0 	skne rx,ry 	skip if register rx <> register ry
            int r0 = cmd & 0x0f;
            int r1 = (data & 0xf0)>>>4;
            final boolean skip = register[r0] != register[r1];
            trace("skip: "+skip+" ( "+Integer.toHexString( register[r0] )+" <> "+Integer.toHexString( register[r1] ) );
            if ( skip ) {
                pc += 2;
            }
        }
        else if ( (cmd & 0xf0) == 0xa0 ) {
            // 0xaxxx 	mvi xxx 	Load index register with constant xxx
            index = (cmd & 0x0f)<<8 | (data & 0xff);
            trace("Index: 0x"+Integer.toHexString( index ) );
        }
        else if ( (cmd & 0xf0) == 0xb0 ) {
            // 0xbxxx 	jmi xxx 	Jump to address xxx+register v0
            int adr = register[0x00] + (cmd & 0x0f)<<8 | (data & 0xff);
            pc = (adr & 0xfff);
        }
        else if ( (cmd & 0xf0) == 0xc0 ) {
            // 0xcrxx 	rand vr,xxx    	vr = random number less than or equal to xxx
            register[ cmd & 0x0f ] = rnd.nextInt( 256 ) & (data & 0xff);
        }
        else if ( (cmd & 0xf0) == 0xd0 )
        {
            int x = cmd & 0x0f;
            int y = (data & 0xf0)>>>4;
            int height = (data & 0x0f);
            if ( height != 0x00 )
            {
                trace("drawSprite @ 0x"+Integer.toHexString( index )+": x="+x+",y="+y+",h="+height);
                register[0x0f] = screen.drawSprite(x,y,height,index) ? 1 : 0;
            }
            else
            {
                trace("drawSpriteExt @ 0x"+Integer.toHexString( index )+": x="+x+",y="+y);
                // 0xdry0 	xsprite rx,ry 	Draws extended sprite at screen location rx,ry
                // As above,but sprite is always 16 x 16. Superchip only, not yet implemented
                register[0x0f] = screen.drawExtendedSprite(x,y,index) ? 1 : 0;
            }
        }
        else if ( (cmd & 0xf0) == 0xe0 )
        {
            int key = cmd & 0x0f;
            if ( data == 0x9e )
            {
                // 0xek9e 	skpr k 	skip if key (register rk) pressed
                // The key is a key number, see the chip-8 documentation
                final boolean pressed = keyboard.isKeyPressed( key );
                trace("keyPressed( "+key+") => "+pressed);
                if ( pressed )
                {
                    pc += 2;
                }
            }
            else if ( data == 0xa1 )
            {
                // 0xeka1 	skup k 	skip if key (register rk) not pressed
                final boolean notPressed = !keyboard.isKeyPressed( key );
                trace("keyNotPressed( "+key+") => "+notPressed);
                if ( notPressed )
                {
                    pc += 2;
                }
            } else {
                illegalInstruction( cmd, data );
            }
        }
        else if ( (cmd & 0xf0) == 0xf0 )
        {
            int r0 = (cmd & 0x0f);
            switch( data )
            {
                case 0x07:   // 0xfr07	gdelay vr 	get delay timer into vr
                   register[r0] = delayTimer.value();
                    trace("Register "+r0+" = "+Integer.toHexString( register[r0] ));
                    break;
                case 0x0a:   // 0xfr0a	key vr wait for keypress,put key in register vr
                    trace("Waiting for keypress");
                    waitForKey = true;
                    keyDestReg = r0;
                    break;
                case 0x15:   // 0xfr15	sdelay vr 	set the delay timer to vr
                    delayTimer.setValue( register[r0] );
                    trace("delay-timer = "+Integer.toHexString( register[r0] ) );
                    waitForDelay = register[r0] > 0;
                    break;
                case 0x18:   // 0xfr18	ssound vr 	set the sound timer to vr
                    soundTimer.setValue( register[r0] );
                    trace("sound-timer = "+Integer.toHexString( register[r0] ) );
                    screen.setBeep( register[ r0 ] > 0 );
                    break;
                case 0x1e:   // 0xfr1e	adi vr add register vr to the index register
                    index = (index + register[ r0 ] ) & 0xfff;
                    trace("Index = 0x"+Integer.toHexString( index ));
                    break;
                case 0x29:   // 0xfr29	font vr 	point I to the sprite for hexadecimal character in vr
                             // Sprite is 5 bytes high
                    index = screen.getGlyphAddr( register[ r0 ] );
                    trace("Index = 0x"+Integer.toHexString( index ));
                    break;
                case 0x30:   // 0xfr30	xfont vr 	point I to the sprite for hexadecimal character in vr
                             // Sprite is 10 bytes high,Super only
                    index = screen.getGlyphAddrExt( register[ r0 ] );
                    trace("Index = 0x"+Integer.toHexString( index ));
                    break;
                case 0x33:   // 0xfr33	bcd vr 	store the bcd representation of register vr at
                             // // location I,I+1,I+2
                             // Doesn't change I
                    String value = Integer.toString( register[r0]);
                    while( value.length() < 3 ) {
                        value = "0" + value;
                    }
                    trace("Writing '"+value+"' @ 0x"+Integer.toHexString( index ) );
                    memory.write( index , value.charAt(0) );
                    memory.write( index+1 , value.charAt(1) );
                    memory.write( index+2 , value.charAt(2) );
                    break;
                case 0x55:   // 0xfr55	str v0-vr 	store registers v0-vr at location I onwards
                             // I is incremented to point to the next location on. e.g. I = I + r + 1
                    trace("Storing registers r0-r"+r0+"' @ 0x"+Integer.toHexString( index ) );
                    for ( int i = 0,ptr = index ; i <= r0 ; ) {
                        memory.write( ptr++, register[i++] );
                    }
                    index = (index + r0 + 1 ) & 0xfff;
                    break;
                case 0x65:   // 0xfx65	ldr v0-vr 	load registers v0-vr from location I onwards as above.
                    trace("Loading registers r0-r"+r0+"' from 0x"+Integer.toHexString( index ) );
                    for ( int i = 0,ptr = index ; i <= r0 ; ) {
                        register[i++] = memory.read( ptr++ );
                    }
                    index = (index + r0 + 1 ) & 0xfff;
                    break;
                default:
                    illegalInstruction( cmd, data );
            }
        } else {
            illegalInstruction( cmd, data );
        }
    }

    private void illegalInstruction(int cmd, int data) {
        throw new RuntimeException("Unhandled opcode: 0x"+Integer.toHexString(cmd<<8|data) );
    }
}