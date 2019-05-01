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

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class Panel extends JPanel
{
    private final Screen screen;

    private final Object IMAGE_LOCK = new Object();

    // @GuardedBy( IMAGE_LOCK )
    private volatile BufferedImage image;
    // @GuardedBy( IMAGE_LOCK )
    private volatile Graphics2D graphics;

    public Panel(Screen screen, Keyboard keyboard)
    {
        this.screen = screen;
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
                    keyboard.keyPressed( key );
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
                    case KeyEvent.VK_Q:
                        return 0x04;
                    case KeyEvent.VK_W:
                        return 0x05;
                    case KeyEvent.VK_E:
                        return 0x06;
                    case KeyEvent.VK_R:
                        return 0x0d;
                    case KeyEvent.VK_A:
                        return 0x07;
                    case KeyEvent.VK_S:
                        return 0x08;
                    case KeyEvent.VK_D:
                        return 0x09;
                    case KeyEvent.VK_F:
                        return 0x0e;
                    case KeyEvent.VK_Y:
                        return 0x0a;
                    case KeyEvent.VK_X:
                        return 0x00b;
                    case KeyEvent.VK_C:
                        return 0x0b;
                    case KeyEvent.VK_V:
                        return 0x0f;
                    default:
                        return -1;
                }
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                int key = keyCode( e );
                if ( key != -1 )
                {
                    keyboard.keyReleased( key );
                }
            }
        };
        addKeyListener( keyListener );
    }

    private Graphics2D createGraphics()
    {
        if ( image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight() )
        {
            if ( graphics != null ) {
                graphics.dispose();
            }
            image = new BufferedImage( getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB );
            graphics = image.createGraphics();
        }
        return graphics;
    }

    public synchronized void draw(Screen screen)
    {
        synchronized (IMAGE_LOCK)
        {
            final Graphics2D g = createGraphics();
            g.setColor( Color.WHITE );
            g.fillRect( 0,0,image.getWidth(),image.getHeight() );
            g.setColor( Color.BLACK );
            final int w = screen.getMode().width();
            final int h = screen.getMode().height();
            int blockWidth = getWidth() / w;
            int blockHeight = getHeight() / h;

            for (int y = 0; y < h; y++)
            {
                for (int x = 0; x < w; x++)
                {
                    if ( screen.readPixel( x, y ) )
                    {
                        g.fillRect( x * blockWidth, y * blockHeight,
                                blockWidth, blockHeight );
                    }
                }
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        synchronized (IMAGE_LOCK)
        {
            if ( image == null )
            {
                super.paintComponent( g );
            }
            else
            {
                g.drawImage( image, 0, 0, getWidth(), getHeight(), null );
            }
        }
    }
}