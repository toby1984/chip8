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
package de.codesourcery.chip8.ui;

import de.codesourcery.chip8.emulator.EmulatorDriver;
import de.codesourcery.chip8.emulator.Screen;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * A {@link JPanel} that renders the emulator's screen.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ScreenPanel extends JPanel
{
    private static final boolean SHOW_FPS = false;

    private final Object IMAGE_LOCK = new Object();

    // @GuardedBy( IMAGE_LOCK )
    private BufferedImage image;

    // @GuardedBy( IMAGE_LOCK )
    private long lastPaint;

    public ScreenPanel(EmulatorDriver driver)
    {
        setFocusable( true );
        requestFocusInWindow();
        final KeyAdapter keyListener = new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                int key = keyCode( e );
                if ( key != -1 )
                {
                    driver.runOnThread(ip -> ip.emulator.keyboard.keyPressed(key));
                }
            }


            @Override
            public void keyReleased(KeyEvent e)
            {
                int key = keyCode( e );
                if ( key != -1 )
                {
                    driver.runOnThread(ip -> ip.emulator.keyboard.keyReleased(key));
                }
            }

            private int keyCode(KeyEvent ev)
            {
                switch ( ev.getKeyCode() )
                {
                    case KeyEvent.VK_1:
                        return 0x01;
                    case KeyEvent.VK_2:
                        return 0x02;
                    case KeyEvent.VK_3:
                        return 0x03;
                    case KeyEvent.VK_4:
                        return 0x0c;
                    // --
                    case KeyEvent.VK_Q:
                        return 0x04;
                    case KeyEvent.VK_W:
                        return 0x05;
                    case KeyEvent.VK_E:
                        return 0x06;
                    case KeyEvent.VK_R:
                        return 0x0d;
                    // --
                    case KeyEvent.VK_A:
                        return 0x07;
                    case KeyEvent.VK_S:
                        return 0x08;
                    case KeyEvent.VK_D:
                        return 0x09;
                    case KeyEvent.VK_F:
                        return 0x0e;
                    // --
                    case KeyEvent.VK_Y:
                        return 0x0a;
                    case KeyEvent.VK_X:
                        return 0x000;
                    case KeyEvent.VK_C:
                        return 0x0b;
                    case KeyEvent.VK_V:
                        return 0x0f;
                    // --
                    default:
                        return -1;
                }
            }
        };
        addKeyListener( keyListener );
    }

    public synchronized void draw(Screen screen)
    {
        synchronized (IMAGE_LOCK)
        {
            if ( image != null )
            {
                screen.copyTo(image);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        synchronized (IMAGE_LOCK)
        {
            if ( image == null ) {
                image = new BufferedImage( Screen.WIDTH, Screen.HEIGHT, BufferedImage.TYPE_BYTE_BINARY );
            }
            g.drawImage( image, 0, 0, getWidth(), getHeight(), null );
            if ( SHOW_FPS )
            {
                final long now = System.currentTimeMillis();
                if (lastPaint != 0)
                {
                    final long elapsed = now - lastPaint;
                    if (elapsed > 0)
                    {
                        g.setColor(Color.RED);
                        g.drawString("FPS: " + (1000 / elapsed), 10, 50);
                    }
                }
                lastPaint = now;
            }
        }
    }
}