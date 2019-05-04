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

import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A timer that invokes listeners ever 1/60th second.
 *
 * Used to implement the delay and sound timers.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SixtyHertzTimer extends Thread
{
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean terminate;
    private final Object SLEEP_LOCK = new Object();

    public SixtyHertzTimer()
    {
        setDaemon(true);
        setName("60-hertz-timer");
    }

    /**
     * Add listener to be invoked every 1/60th second.
     *
     * @param r
     */
    public void addListener(Runnable r)
    {
        Validate.notNull(r, "r must not be null");
        this.listeners.add( r );
    }

    /**
     * Remove listener to be invoked every 1/60th second.
     *
     * @param r
     */
    public void removeListener(Runnable r)
    {
        Validate.notNull(r, "r must not be null");
        this.listeners.remove( r );
    }

    @Override
    public void run()
    {
        int modCount = -1;
        Runnable[] copy = new Runnable[0];
        while ( ! terminate )
        {
            listeners.forEach(x ->
            {
                try {
                    x.run();
                }
                catch (RuntimeException e) {
                    e.printStackTrace();
                }
            });

            try
            {
                synchronized(SLEEP_LOCK)
                {
                    if (!terminate)
                    {
                        SLEEP_LOCK.wait(1000 / 60);
                    }
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void terminate()
    {
        terminate = true;
        synchronized (SLEEP_LOCK) {
            SLEEP_LOCK.notifyAll();
        }
    }
}