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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import java.util.function.Consumer;

public class Main
{
    public static final String PROGRAM_CLASSPATH = "/space_invaders.ch8";

    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        final File configFile = new File( System.getProperty("user.home"), ".chip8Config.properties");
        final MainFrame.IConfigurationProvider configProvider = new MainFrame.IConfigurationProvider()
        {
            private volatile Properties configuration;

            @Override
            public synchronized Properties load()
            {
                if ( configuration != null ) {
                    return configuration;
                }
                final Properties props = new Properties();
                if ( configFile.exists() ) {
                    System.out.println("Loading configuration from "+configFile.getAbsolutePath());
                    try (FileInputStream in = new FileInputStream(configFile ) ){
                        props.load(in );
                    }
                    catch(IOException e)
                    {
                        System.err.println("Failed to load configuration from "+configFile.getAbsolutePath());
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Found no config file "+configFile.getAbsolutePath());
                }
                configuration = props;
                return configuration;
            }

            @Override
            public synchronized void save()
            {
                if ( configuration == null ) {
                    return;
                }
                try (FileOutputStream out = new FileOutputStream(configFile ) )
                {
                    System.out.println("Saving configuration to "+configFile.getAbsolutePath());
                    configuration.store(out, "Automatically generated, changes will be overwritten");
                }
                catch (IOException e)
                {
                    System.err.println("Failed to save configuration to "+configFile.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        };

        final Memory memory = new Memory( 4096);
        final Screen screen = new Screen( memory );
        final Keyboard keyboard = new Keyboard();
        final Consumer<Interpreter> hook = emu -> {

            try
            {
                final File file = new ConfigSerializer( configProvider.load() , "global" ).getLastBinary();
                if ( file != null )
                {
                        try ( FileInputStream in = new FileInputStream(file) )
                        {
                            emu.memory.load(in, 0x200);
                        }
                        catch(Exception e)
                        {
                            System.err.println("Failed to load executable from "+file.getAbsolutePath());
                            e.printStackTrace();
                        }
                }
                else
                {
                    emu.memory.load(PROGRAM_CLASSPATH, 0x200);
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        };
        final Interpreter ip = new Interpreter( memory,screen,keyboard,hook);
        final InterpreterDriver controller = new InterpreterDriver(ip);
        SwingUtilities.invokeAndWait( () -> new MainFrame( controller, configProvider));
    }
}