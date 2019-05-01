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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class InterpreterDriver
{
    public final Interpreter interpreter;

    @FunctionalInterface
    public interface IDriverCallback
    {
        void invoke(InterpreterDriver controller);
    }

    @FunctionalInterface
    public interface IStateListener
    {
        void stateChanged(InterpreterDriver controller, boolean newState);
    }

    private final BlockingQueue<IDriverCallback> tickListeners =
            new ArrayBlockingQueue<>(100);

    private final BlockingQueue<IStateListener> stateListeners =
            new ArrayBlockingQueue<>(100);

    private final ControllerThread thread = new ControllerThread();

    public enum CmdType {
        START,STOP,STEP,RESET,RUN,TERMINATE
    }

    private static final class Cmd
    {
        public final Runnable runnable;
        public final CmdType type;

        private Cmd(CmdType type,Runnable runnable) {
            this.type = type;
            this.runnable = runnable;
        }

        public void onReceive() {
            runnable.run();
        }
    }

    private final class ControllerThread extends Thread
    {
        private final BlockingQueue<Cmd> cmdQueue =
                new ArrayBlockingQueue<>(50,true);

        private boolean running;
        private long cycleCount=0;
        private long delay=1000;
        private int tickInterval = 10000;
        private volatile boolean terminated;

        public double dummyValue = 123;

        public void submit(Cmd cmd)
        {
            if ( terminated ) {
                throw new IllegalStateException("Cannot submit command to terminated thread");
            }
            cmdQueue.add( cmd );
        }

        private void delay()
        {
            double v = dummyValue;
            for ( long i = delay ; i > 0 ; i--) {
                v = v + Math.sqrt(v);
            }
            dummyValue = v;
        }

        @Override
        public void run()
        {
            try {
                doRun();
            } finally {
                terminated = true;
            }
        }

        private void doRun()
        {
            while( true)
            {
                Cmd cmd;
                if ( running )
                {
                    cmd = cmdQueue.poll();
                }
                else
                {
                    try
                    {
                        cmd = cmdQueue.take();
                    }
                    catch (InterruptedException e)
                    {
                        continue;
                    }
                }
                if ( cmd != null )
                {
                    cmd.onReceive();
                    switch(cmd.type)
                    {
                        case TERMINATE:
                            terminated = true;
                            while( ( cmd = cmdQueue.poll() ) != null ) {
                                cmd.onReceive();
                            }
                            return;
                        case RUN:
                            continue;
                        case RESET:
                            running = false;
                            interpreter.reset();
                            invokeTickListeners();
                            invokeStateListeners( false );
                            continue;
                        case START:
                            running = true;
                            invokeStateListeners( true );
                            continue;
                        case STOP:
                            running = false;
                            invokeStateListeners( false );
                            continue;
                        case STEP:
                            invokeStateListeners( true );
                            break;
                    }
                }
                boolean stateListenersInvoked = false;
                boolean tickListenersInvoked = false;
                try
                {
                    interpreter.tick();
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    running = false;
                    stateListenersInvoked = true;
                    invokeStateListeners( false );
                }
                if ( (cycleCount++ % tickInterval) == 0 )
                {
                    invokeTickListeners();
                    tickListenersInvoked=true;
                }
                if ( cmd != null && cmd.type == CmdType.STEP )
                {
                    running = false;
                    if ( ! tickListenersInvoked ) {
                        invokeTickListeners();
                    }
                    if (! stateListenersInvoked )
                    {
                        invokeStateListeners( false );
                    }
                } else {
                    delay();
                }
            }
        }

        private void invokeStateListeners(boolean newState)
        {
            stateListeners.forEach( l ->
            {
                try
                {
                    l.stateChanged( InterpreterDriver.this, newState );
                }
                catch(Exception e)
                {
                    System.err.println("State listener "+l+" threw exception.");
                    e.printStackTrace();
                }
            } );
        }

        private void invokeTickListeners()
        {
            tickListeners.forEach( l ->
            {
                try
                {
                    l.invoke( InterpreterDriver.this );
                }
                catch(Exception e)
                {
                    System.err.println("Tick listener "+l+" threw exception.");
                    e.printStackTrace();
                }
            } );
        }
    }

    public InterpreterDriver(Interpreter interpreter)
    {
        this.interpreter = interpreter;
        thread.setDaemon( true );
        thread.setName("controller-thread");
        thread.start();
    }

    public void start()
    {
        execute(CmdType.START);
    }

    public void reset() {
        execute(CmdType.RESET);
    }

    public void stop() {
        execute(CmdType.STOP);
    }

    public void terminate()
    {
        thread.submit(new Cmd(CmdType.TERMINATE,() -> {} ) );
    }

    public void step()
    {
        execute(CmdType.STEP);
    }

    public void runOnThread(IDriverCallback callback)
    {
        thread.submit(new Cmd(CmdType.RUN, () -> callback.invoke(this ) ) );
    }

    private void execute(CmdType type)
    {
        final CountDownLatch latch = new CountDownLatch(1);
        thread.submit( new Cmd(type, latch::countDown) );
        try
        {
            latch.await();
        }
        catch(InterruptedException e) {
            /* can't help it */
        }
    }

    public void registerTickListener(IDriverCallback listener) {
        this.tickListeners.add( listener );
    }

    public void removeTickListener(IDriverCallback listener) {
        this.tickListeners.remove( listener );
    }

    public void registerStateListener(IStateListener listener) {
        this.stateListeners.add( listener );
    }

    public void removeStateListener(IStateListener listener) {
        this.stateListeners.remove( listener );
    }
}