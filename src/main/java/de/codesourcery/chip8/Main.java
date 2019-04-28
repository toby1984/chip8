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

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

public class Main extends JFrame
{
    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        SwingUtilities.invokeAndWait( () ->
        {
            try
            {
                new Main().run();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        } );
    }

    public void run() throws IOException
    {
        final Memory memory = new Memory( 4096);
        final Screen screen = new Screen( memory );
        final Panel panel = new Panel(screen);
        final Keyboard keyboard = new Keyboard();
        final Consumer<Interpreter> hook = emu -> {

            try
            {
                emu.memory.load( "/logo.ch8", 0x200 );
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        };
        final Interpreter ip = new Interpreter( memory,screen,keyboard,hook);

        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        panel.setPreferredSize( new Dimension(640,320) );
        panel.addKeyListener( new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                int key = keyCode( e );
                if ( key != -1 ) {
                    keyboard.keyPressed( key );
                }
            }

            private int keyCode(KeyEvent ev)
            {
                switch( ev.getKeyCode() ) {
                    case KeyEvent.VK_1:
                        return 0x00;
                    case KeyEvent.VK_2:
                        return 0x01;
                    case KeyEvent.VK_3:
                        return 0x02;
                    case KeyEvent.VK_4:
                        return 0x03;
                    case KeyEvent.VK_Q:
                        return 0x04;
                    case KeyEvent.VK_W:
                        return 0x05;
                    case KeyEvent.VK_E:
                        return 0x06;
                    case KeyEvent.VK_R:
                        return 0x07;
                    case KeyEvent.VK_A:
                        return 0x08;
                    case KeyEvent.VK_S:
                        return 0x09;
                    case KeyEvent.VK_D:
                        return 0x0a;
                    case KeyEvent.VK_F:
                        return 0x0b;
                    case KeyEvent.VK_Y:
                        return 0x0c;
                    case KeyEvent.VK_X:
                        return 0x0d;
                    case KeyEvent.VK_C:
                        return 0x0e;
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
                if ( key != -1 ) {
                    keyboard.keyReleased( key );
                }
            }
        });

        getContentPane().setLayout( new GridBagLayout() );

        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = cnstrs.weighty = 1.0;
        cnstrs.fill = GridBagConstraints.BOTH;
        getContentPane().add( panel , cnstrs );
        setVisible( true );
        pack();
        setLocationRelativeTo( null );

        final Thread emuThread = new Thread(() ->
        {
            while( true )
            {
                try
                {
                    Thread.sleep(1000/500);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                ip.tick();
            }
        });
        emuThread.setDaemon( true );
        emuThread.start();

        new Timer(1000/60, ev -> panel.repaint() ).start();
    }
}