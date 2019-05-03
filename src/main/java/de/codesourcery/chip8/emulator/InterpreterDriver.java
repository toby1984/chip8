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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class InterpreterDriver
{
    private static final float INSTRUCTIONS_PER_SECOND = 1500;
    public static final int MAX_DELAY = 600000;

    public enum Reason {
        STOPPED,
        STARTED,
        STOPPED_BREAKPOINT
    }
    @FunctionalInterface
    public interface IDriverCallback
    {
        void invoke(InterpreterDriver controller);
    }

    @FunctionalInterface
    public interface IStateListener
    {
        void stateChanged(InterpreterDriver controller, Reason reason);
    }

    public final Interpreter interpreter;

    private final Breakpoints enabledBreakpoints = new Breakpoints( 4096 );
    private final Breakpoints disabledBreakpoints = new Breakpoints( 4096 );

    private final BlockingQueue<Runnable> shutdownListeners =
        new ArrayBlockingQueue<>(100);

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
        public final Consumer<ControllerThread> runnable;
        public final CmdType type;

        private Cmd(CmdType type,Consumer<ControllerThread> runnable) {
            this.type = type;
            this.runnable = runnable;
        }

        public void onReceive(ControllerThread thread) {
            runnable.accept(thread);
        }
    }

    private final class ControllerThread extends Thread
    {
        private final BlockingQueue<Cmd> cmdQueue =
                new ArrayBlockingQueue<>(50,true);

        private boolean running;
        private long lastTimestamp;
        private long cycleCount=0;
        private long delay=1000;
        private int tickInterval = 10000;
        private volatile boolean terminated;

        public double dummyValue = 123;

        private void setRunning(boolean newState, Reason reason)
        {
            final boolean oldState = running;
            if ( ! oldState && newState) {
                // start
                cycleCount = 0;
                lastTimestamp = System.nanoTime();
                invokeStateListeners( reason );
            }
            else if ( oldState && ! newState )
            {
                invokeTickListeners();
                invokeStateListeners( reason );
            }
            this.running = newState;
        }

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
                v = v + v*1.35*i + v / 3.74;
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
            boolean ignoreBreakpoint = false;
            while( true)
            {
                Cmd cmd;
                try
                {
                    cmd = running ? cmdQueue.poll() : cmdQueue.take();
                }
                catch (InterruptedException e)
                {
                    continue;
                }
                if ( cmd != null )
                {
                    cmd.onReceive(this);
                    switch(cmd.type)
                    {
                        case TERMINATE:
                            terminated = true;
                            setRunning( false , Reason.STOPPED);
                            while( ( cmd = cmdQueue.poll() ) != null ) {
                                cmd.onReceive(this);
                            }
                            return;
                        case RUN:
                            continue;
                        case RESET:
                            setRunning( false , Reason.STOPPED);
                            interpreter.reset();
                            continue;
                        case START:
                            ignoreBreakpoint = ! running;
                            setRunning( true, Reason.STARTED );
                            continue;
                        case STOP:
                            setRunning( false, Reason.STOPPED );
                            continue;
                        case STEP:
                            ignoreBreakpoint = ! running;
                            setRunning( true, Reason.STARTED);
                            break;
                    }
                }

                if ( ! ignoreBreakpoint && enabledBreakpoints.checkBreakpointHit(interpreter.pc ) )
                {
                    setRunning( false , Reason.STOPPED_BREAKPOINT);
                    continue;
                }

                ignoreBreakpoint = false;

                try
                {
                    interpreter.tick();
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    setRunning( false, Reason.STOPPED );
                    continue;
                }
                if ( (++cycleCount % tickInterval) == 0 )
                {
                    invokeTickListeners();
                }
                if ( cmd != null && cmd.type == CmdType.STEP )
                {
                    System.out.println("Step");
                    setRunning( false, Reason.STOPPED );
                } else {
                    delay();
                }
            }
        }

        private void invokeStateListeners(Reason reason)
        {
            stateListeners.forEach( l ->
            {
                try
                {
                    l.stateChanged( InterpreterDriver.this, reason );
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
        shutdownListeners.forEach(x -> { try { x.run(); } catch(Exception e) {} } );
        execute(CmdType.TERMINATE);
    }

    public void step()
    {
        execute(CmdType.STEP);
    }

    public void setSpeed(float value)
    {
        float factor = Math.max(0, Math.min(1,value) );
        final long newDelay = (long) ((1.0f-factor)*MAX_DELAY);
        System.out.println("Setting speed to "+(100.0f*factor)+" % (delay: "+newDelay+")");
        thread.submit( new Cmd( CmdType.RUN, thread ->
        {
            thread.delay = newDelay;
        }));
    }

    public void runOnThread(IDriverCallback callback)
    {
        if ( Thread.currentThread() == thread ) {
            callback.invoke( this);
        }
        else
        {
            final CountDownLatch latch = new CountDownLatch(1);
            thread.submit( new Cmd( CmdType.RUN, thread ->
            {
                try
                {
                    callback.invoke(this);
                } finally {
                    latch.countDown();
                }
            }));
            try
            {
                latch.await();
            }
            catch(InterruptedException e) {
                /* can't help it */
            }
        }
    }

    private void execute(CmdType type)
    {
        final CountDownLatch latch = new CountDownLatch(1);
        thread.submit( new Cmd(type, thread -> latch.countDown() ) );
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

    public List<Breakpoint> getAllBreakpoints()
    {
        final List<Breakpoint> list = new ArrayList<>();

        runOnThread( ip -> {
            synchronized(list)
            {
                enabledBreakpoints.getAll( list );
                disabledBreakpoints.getAll( list );
            }
        });
        synchronized(list)
        {
            return list;
        }
    }

    public void addBreakpoint(Breakpoint bp,boolean enabled)
    {
        runOnThread( ip -> {
            enabledBreakpoints.remove( bp );
            disabledBreakpoints.remove( bp );
            if ( enabled ) {
                enabledBreakpoints.add(bp);
            } else {
                disabledBreakpoints.add(bp);
            }
        });
    }

    public void removeBreakpoint(Breakpoint bp)
    {
        runOnThread( ip -> {
            enabledBreakpoints.remove( bp );
            disabledBreakpoints.remove( bp );
        });
    }

    public void toggle(Breakpoint bp)
    {
        runOnThread(ip ->
        {
            if ( enabledBreakpoints.contains(bp) || disabledBreakpoints.contains(bp) ) {
                enabledBreakpoints.remove(bp);
                disabledBreakpoints.remove(bp);
            }
            else
            {
                enabledBreakpoints.add(bp);
            }
        });
    }

    public void changeState(Breakpoint bp,boolean enable)
    {
        runOnThread( ip ->
        {
            if ( enable )
            {
                disabledBreakpoints.remove( bp );
                enabledBreakpoints.add(bp);
            } else {
                enabledBreakpoints.remove( bp );
                disabledBreakpoints.add( bp );
            }
        });
    }

    public void addShutdownListener(Runnable r)
    {
        this.shutdownListeners.add( r );
    }
}