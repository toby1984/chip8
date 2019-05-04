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

import java.util.HashSet;
import java.util.Set;

/**
 * Keyboard interface.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public abstract class Keyboard
{
    private final Set<Integer> pressedKeys = new HashSet<>();

    /**
     * Tell the emulation that a key has been pressed.
     *
     * @param key
     */
    public void keyPressed(int key) {
        Validate.isTrue(key>=0 && key <= 0x0f);
        pressedKeys.add(key);
        getDriver().keyPressed(key);
    }

    /**
     * Tell the emulation that a key has been released.
     *
     * @param key
     */
    public void keyReleased(int key)
    {
        Validate.isTrue(key>=0 && key <= 0x0f);
        pressedKeys.remove(key);
        getDriver().keyReleased(key);
    }

    public boolean isKeyPressed(int key)
    {
        return pressedKeys.contains(key);
    }

    /**
     * Clears the internal keyboard buffer.
     */
    public void reset()
    {
        pressedKeys.clear();
    }

    protected abstract EmulatorDriver getDriver();
}