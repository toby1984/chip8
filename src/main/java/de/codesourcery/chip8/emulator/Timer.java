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

/**
 * Abstract base-class for the sound and delay timers.
 *
 * @author tobias.gierke@code-sourcery.de
 * @see SixtyHertzTimer
 */
public abstract class Timer implements Runnable
{
    private final String name;

    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private int value;

    /**
     * Create instance.
     *
     * @param name timer name (debugging only)
     */
    public Timer(String name)
    {
        Validate.notBlank( name, "name must not be null or blank");
        this.name = name;
    }

    @Override
    public String toString()
    {
        return "timer-"+name;
    }

    public void run()
    {
        boolean triggered = false;
        synchronized (LOCK)
        {
            if ( value > 0 )
            {
                value--;
                if ( value == 0 )
                {
                    triggered = true;
                }
            }
        }
        if ( triggered ) {
            triggered();
        }
    }

    /**
     * Stops this timer.
     */
    public void reset()
    {
        synchronized (LOCK)
        {
            value = 0;
        }
    }

    /**
     * Sets the timer's current value.
     *
     * @param value
     */
    public void setValue(int value)
    {
        synchronized (LOCK)
        {
            this.value = value & 0xff;
        }
    }

    /**
     * Returns the timer's current value.
     * @return
     */
    public int value()
    {
        synchronized (LOCK)
        {
            return value;
        }
    }

    protected abstract void triggered();
}