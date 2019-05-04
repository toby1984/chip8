package de.codesourcery.chip8.emulator;

import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class ScreenTest extends TestCase
{

    private static final int WARMUP_COUNT = 10000;
    private static final int LOOP_COUNT = 1000000;
    private Screen screen;
    private Memory memory;

    @Override
    protected void setUp() throws Exception
    {
        memory = new Memory(4096);
        screen = new Screen( memory );
    }

    public void testFastPerformance()
    {
        final byte[] data = new byte[]{1,2,3,4,5,6,7,8};

        memory.write(0,data );

        long best = 12345678;
        for ( int i = 20; i > 0 ; i-- ) {

            long start = System.currentTimeMillis();
            for (int j = 0 ; j < 100000 ; j++)
            {
                for (int x = 0; x < (64 - 8); x++)
                {
                    screen.drawSpriteFast(x, 1, 8, 0);
                }
            }
            long end = System.currentTimeMillis();
            best = Math.min( best , end-start );
        }
        System.out.println("FAST TIME (best): "+best+" ms");
    }

    public void testSlowPerformance()
    {
        final byte[] data = new byte[]{1,2,3,4,5,6,7,8};

        memory.write(0,data );

        long best = 12345678;
        for ( int i = 20; i > 0 ; i-- ) {

            long start = System.currentTimeMillis();
            for (int j = 0 ; j < 100000 ; j++)
            {
                for (int x = 0; x < (64 - 8); x++)
                {
                    screen.drawSpriteSlow(x, 1, 8, 0);
                }
            }
            long end = System.currentTimeMillis();
            best = Math.min( best , end-start );
        }
        System.out.println("SLOW TIME (best): "+best+" ms");
    }

    public void testDetectClearPixels1()
    {
        final byte[] data = new byte[8];
        Arrays.fill(data,(byte) 0x08);

        int mask = 0b1000_0000;
        for ( int x = 0 ; x < 8 ; x++ )
        {
            System.out.println("x = "+x+", MASK: "+binary(mask,8));
            memory.write( 0,mask);
            screen.clear();
            screen.data[0] = (byte) 0xff;
            screen.data[1] = (byte) 0xff;

            int expected = (0xffff ^ (mask<<(8-x)));
            boolean pixelsCleared = screen.drawSpriteFast( x,0,1,0);
            final String lmsg = "x = "+leftPad(Integer.toString(x),2 )+ ", %" + binary( mask,8);
            assertTrue( "Cleared pixel check failed for " + lmsg, pixelsCleared );
            int actual = (screen.data[0] & 0xff) << 8 | (screen.data[1] & 0xff);
            System.out.println("EXPECTED: "+binary(expected,16));
            System.out.println("GOT     : "+binary(actual,16));
            assertEquals( "Screen contents mismatch", expected,actual );
            System.out.println( "Success for " + lmsg );
        }
    }

    public void testDetectClearPixels2()
    {
        final byte[] data = new byte[8];
        Arrays.fill(data,(byte) 0x08);

        int mask = 0b1000_0000;
        for ( int x = 0 ; x < 8 ; x++ )
        {
            System.out.println("x = "+x+", MASK: "+binary(mask,8));
            memory.write( 0,mask);
            screen.clear();
            screen.data[0] = (byte) 0x00;
            screen.data[1] = (byte) 0x00;

            int expected = (0x0000 ^ (mask<<(8-x)));
            boolean pixelsCleared = screen.drawSpriteFast( x,0,1,0);
            final String lmsg = "x = "+leftPad(Integer.toString(x),2 )+ ", %" + binary( mask,8);
            assertFalse( "Cleared pixel check failed for " + lmsg, pixelsCleared );
            int actual = (screen.data[0] & 0xff) << 8 | (screen.data[1] & 0xff);
            System.out.println("EXPECTED: "+binary(expected,16));
            System.out.println("GOT     : "+binary(actual,16));
            assertEquals( "Screen contents mismatch", expected,actual );
            System.out.println( "Success for " + lmsg );
        }
    }

    private static String leftPad(String value,int len) {
        return StringUtils.leftPad(value,len,'0');
    }

    private static String binary(int value,int digits) {
        return "%"+leftPad( Integer.toBinaryString( value ), digits );
    }
}