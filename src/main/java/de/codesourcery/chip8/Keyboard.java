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

import java.util.HashSet;
import java.util.Set;

public class Keyboard
{
    public static final int NO_KEY = -1;

    // @GuardedBy( pressedKeys )
    private final Set<Integer> pressedKeys = new HashSet<>();

    public void keyPressed(int key) {
        System.out.println("Pressed: "+key);
        synchronized (pressedKeys)
        {
            pressedKeys.add( Integer.valueOf( key ) );
        }
    }

    public void keyReleased(int key)
    {
        System.out.println("Released: "+key);
        synchronized (pressedKeys)
        {
            pressedKeys.remove( Integer.valueOf( key ) );
        }
    }

    public boolean isKeyPressed(int key)
    {
        synchronized (pressedKeys)
        {
            return pressedKeys.contains( Integer.valueOf( key ) );
        }
    }

    public int readKey()
    {
        synchronized (pressedKeys)
        {
            if ( pressedKeys.isEmpty() )
            {
                return NO_KEY;
            }
            return pressedKeys.iterator().next();
        }
    }

    public void reset()
    {
        synchronized (pressedKeys)
        {
            pressedKeys.clear();
        }
    }
}