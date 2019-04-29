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

public class Panel extends JPanel
{
    private final Screen screen;

    public Panel(Screen screen)
    {
        this.screen = screen;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent( g );
        if ( screen != null )
        {
            g.setColor( Color.BLACK );
            final int w = screen.getMode().width();
            final int h = screen.getMode().height();
            int blockWidth = getWidth() / w;
            int blockHeight = getHeight() / h;

            for ( int y =0 ; y < h ; y++ )
            {
                for (int x = 0; x < w; x++)
                {
                    if ( screen.readPixel( x,y ) )
                    {
                        g.fillRect( x*blockWidth, y*blockHeight,
                                blockWidth, blockHeight );
                    }
                }
            }
        }
    }
}