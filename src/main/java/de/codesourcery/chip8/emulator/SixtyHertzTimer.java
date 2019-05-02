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

import java.util.ArrayList;
import java.util.List;

public class SixtyHertzTimer extends Thread
{
    private final List<Runnable> listeners = new ArrayList<>();

    private int modificationCount = 0;

    public SixtyHertzTimer()
    {
        setDaemon(true);
        setName("60-hertz-timer");
    }

    public void addListener(Runnable r)
    {
        synchronized (listeners)
        {
            this.listeners.add( r );
            modificationCount++;
        }
    }

    public void removeListener(Runnable r)
    {
        synchronized (listeners)
        {
            this.listeners.remove( r );
            modificationCount++;
        }
    }

    @Override
    public void run()
    {
        int modCount = -1;
        Runnable[] copy = new Runnable[0];
        while ( true )
        {
            synchronized (listeners)
            {
                if ( modCount != modificationCount )
                {
                    if ( copy.length != listeners.size() )
                    {
                        copy = new Runnable[listeners.size()];
                    }
                    for (int i = 0; i < listeners.size(); i++)
                    {
                        copy[i] = listeners.get( i );
                    }
                }
            }

            for (int i = 0, len = copy.length; i < len; i++)
            {
                try { copy[i].run(); } catch (RuntimeException e) { e.printStackTrace(); }
            }
            try { Thread.sleep( 1000 / 60 );  } catch (InterruptedException e) { e.printStackTrace(); }
        }
    }
}