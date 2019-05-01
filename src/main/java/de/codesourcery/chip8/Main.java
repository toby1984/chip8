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

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

public class Main
{
    public static final String PROGRAM_CLASSPATH = "/space_invaders.ch8";

    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        final Memory memory = new Memory( 4096);
        final Screen screen = new Screen( memory );
        final Keyboard keyboard = new Keyboard();
        final Consumer<Interpreter> hook = emu -> {

            try
            {
                emu.memory.load( PROGRAM_CLASSPATH, 0x200 );
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        };
        final Interpreter ip = new Interpreter( memory,screen,keyboard,hook);
        final Controller controller = new Controller(ip);
        SwingUtilities.invokeAndWait( () -> new Emulator( controller ) );
    }
}