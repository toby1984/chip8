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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Responsible for controlling/driving the emulation in response to external user input.
 *
 * Since the actual emulation is not thread-safe, all changes to /inspection of emulation state
 * needs to be executed on the emulator driver's thread (see {@link #runOnThread(IDriverCallback)}.
 *
 * @author tobias.gierke@code-sourcery.de
 * @see Emulator
 */
public class EmulatorDriver
{
    private static boolean INVOKE_TICK_LISTENERS_WHILE_RUNNING = false;

    static final boolean PRINT_CYCLES_PER_SECOND = false;

    private static final int MAX_DELAY = 1000000;
    private static final int MAX_TICK_INTERVAL = 45000000;

    static final int FLAG_WAIT_DELAY = 1;
    static final int FLAG_WAIT_KEY_PRESS = 2;
    static final int FLAG_WAIT_KEY_RELEASE = 4;

    /**
     * A callback invoked on the emulation thread.
     * @see EmulatorDriver#runOnThread(IDriverCallback)
     */
    @FunctionalInterface
    public interface IDriverCallback
    {
        void invoke(EmulatorDriver controller);
    }

    @FunctionalInterface
    public interface IDriverCallbackWithValue<T>
    {
        T invoke(EmulatorDriver controller);
    }

    /**
     * A callback invoked whenever the emulation starts or stops.
     *
     * @see EmulatorDriver#runOnThread(IDriverCallback)
     */
    @FunctionalInterface
    public interface IStateListener
    {
        void stateChanged(EmulatorDriver controller, Reason reason);
    }

    /**
     * Reason why a {@link IStateListener} got invoked.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    public enum Reason
    {
        /**
         * Emulation stopped (unspecified).
         */
        STOPPED,
        /**
         * Emulation started (unspecified).
         */
        STARTED,
        /**
         * Emulation stopped because a breakpoint was hit.
         */
        STOPPED_BREAKPOINT
    }

    public final Emulator emulator;

    private final Breakpoints enabledBreakpoints = new Breakpoints("enabled");
    private final Breakpoints disabledBreakpoints = new Breakpoints("disabled");

    private final CopyOnWriteArrayList<Runnable> shutdownListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IDriverCallback> tickListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IDriverCallback> breakpointChangeListeners = new CopyOnWriteArrayList<>();

    private final ControllerThread thread = new ControllerThread();

    public enum CmdType
    {
        START,STOP,STEP,RESET,RUN,TERMINATE,CHANGE_BREAKPOINTS
    }

    static final class Cmd
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
        private final CommandQueue cmdQueue = new CommandQueue();

        private volatile boolean terminated;
        private long delay=1000;
        public double dummyValue = 123;
        private long tickInterval = MAX_TICK_INTERVAL/2;

        private void keyPressed(int key)
        {
            if ( cmdQueue.isSet( FLAG_WAIT_KEY_PRESS ) )
            {
                emulator.pressedKey = key;
                cmdQueue.set( FLAG_WAIT_KEY_RELEASE );
                cmdQueue.clear( FLAG_WAIT_KEY_PRESS );
            }
        }

        private void keyReleased(int key)
        {
            if ( cmdQueue.isSet( FLAG_WAIT_KEY_RELEASE) && key == emulator.pressedKey )
            {
                emulator.register[ emulator.keyDestReg ] = key;
                cmdQueue.clear( FLAG_WAIT_KEY_RELEASE );
            }
        }

        private void delayTimerTriggered()
        {
            cmdQueue.clear(FLAG_WAIT_DELAY);
        }

        private boolean setRunning(boolean oldState, boolean newState, Reason reason)
        {
            if ( ! oldState && newState)
            {
                invokeStateListeners( reason );
            }
            else if ( oldState && ! newState )
            {
                invokeTickListeners();
                invokeStateListeners( reason );
            }
            return newState;
        }

        private void submit(Cmd cmd)
        {
            if ( terminated ) {
                throw new IllegalStateException("Cannot submit command to terminated thread");
            }
            cmdQueue.add( cmd );
        }

        private void delay()
        {
            double v = dummyValue;
            for ( long i = delay ; i > 0 ; i--)
            {
                v += v * 1.35 * i + v / 3.74;
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
            long cyclesUntilTick = tickInterval;

            boolean running = false;
            boolean ignoreBreakpoint = false;
            boolean isStepping = false;

            while( true)
            {
                Cmd cmd = running ? cmdQueue.poll() : cmdQueue.take();
                if ( cmd != null )
                {
                    cmd.onReceive(this);
                    switch(cmd.type)
                    {
                        case TERMINATE:
                            terminated = true;
                            cmdQueue.setWaitFlags(0); // prevent cmdQueue#poll() from blocking
                            setRunning( running, false , Reason.STOPPED);
                            while( ( cmd = cmdQueue.poll() ) != null ) {
                                cmd.onReceive(this);
                            }
                            shutdownListeners.forEach(x -> { try { x.run(); } catch(Exception e) {
                                e.printStackTrace();
                            }} );
                            return; /* stop thread */
                        case CHANGE_BREAKPOINTS:
                            // hasEnabledBreakpoints = enabledBreakpoints.isNotEmpty();
                        case RUN:
                            continue;
                        case RESET:
                            running = setRunning( running, false , Reason.STOPPED);
                            enabledBreakpoints.clearTemporary();
                            disabledBreakpoints.clearTemporary();
                            emulator.reset();
                            cmdQueue.reset();
                            // hasEnabledBreakpoints = enabledBreakpoints.isNotEmpty();
                            continue;
                        case STEP:
                            isStepping = true;
                        case START:
                            cmdQueue.totalCycleCount = 0;
                            cmdQueue.lastTimestamp = System.currentTimeMillis();
                            if ( ! running )
                            {
                                ignoreBreakpoint = true;
                                running = setRunning( running,true, Reason.STARTED);
                            }
                            if ( cmd.type == CmdType.STEP) {
                                break;
                            }
                            continue;
                        case STOP:
                            running = setRunning( running, false, Reason.STOPPED );
                            isStepping = false;
                            continue;
                    }

                    if ( cmdQueue.isWaiting() )
                    {
                        continue;
                    }
                }

                if ( ! ignoreBreakpoint && enabledBreakpoints.checkBreakpointHit(emulator.pc ) )
                {
                    running = setRunning( running, false , Reason.STOPPED_BREAKPOINT);
                    continue;
                }

                ignoreBreakpoint = false;

                try
                {
                    emulator.executeOneInstruction(cmdQueue);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    running = setRunning( running, false, Reason.STOPPED );
                    continue;
                }
                if ( PRINT_CYCLES_PER_SECOND )
                {
                    cmdQueue.totalCycleCount++;
                    if ((cmdQueue.totalCycleCount % tickInterval) == 0 && cmdQueue.lastTimestamp != 0)
                    {
                        final float elapsedSeconds = (System.currentTimeMillis() - cmdQueue.lastTimestamp) / 1000f;
                        final long cyclesPerSecond = (long) (cmdQueue.totalCycleCount / elapsedSeconds);
                        System.out.println("cycles per second: " + cyclesPerSecond);
                    }
                }

                if ( --cyclesUntilTick >= 0 )
                {
                    cyclesUntilTick = tickInterval;
                    if ( INVOKE_TICK_LISTENERS_WHILE_RUNNING )
                    {
                        invokeTickListeners();
                    }
                }

                if ( isStepping )
                {
                    running = setRunning( running, false, Reason.STOPPED );
                    isStepping = false;
                } else if ( delay > 0 ) {
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
                    l.stateChanged( EmulatorDriver.this, reason );
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
                    l.invoke( EmulatorDriver.this );
                }
                catch(Exception e)
                {
                    System.err.println("Tick listener "+l+" threw exception.");
                    e.printStackTrace();
                }
            } );
        }
    }

    /**
     * Create instance.
     *
     * @param emulator emulator to drive
     */
    public EmulatorDriver(Emulator emulator)
    {
        Validate.notNull(emulator, "interpreter must not be null");
        this.emulator = emulator;
        thread.setDaemon( true );
        thread.setName("controller-thread");
        thread.start();
    }

    /**
     * Start emulation.
     *
     * If the emulation is already running, nothing happens.
     * @throws IllegalStateException if this driver has already been destroyed.
     */
    public void start()
    {
        execute(CmdType.START);
    }

    /**
     * Stops and resets emulation.
     * @throws IllegalStateException if this driver has already been destroyed.
     * @see #destroy()
     */
    public void reset() {
        execute(CmdType.RESET);
    }

    /**
     * Stops the emulation.
     *
     * If the emulation is already stopped, nothing happens.
     * @throws IllegalStateException if this driver has already been destroyed.
     * @see #destroy()
     */
    public void stop() {
        execute(CmdType.STOP);
    }

    /**
     * Destroys this emulation driver.
     *
     * @throws IllegalStateException if this driver has already been destroyed.
     * @see #destroy()
     */
    public void destroy()
    {
        execute(CmdType.TERMINATE);
    }

    /**
     * Single-step the next instruction.
     *
     * Emulation will only execute the next instruction.
     *
     * @throws IllegalStateException if this driver has already been destroyed.
     * @see #destroy()
     */
    public void step()
    {
        execute(CmdType.STEP);
    }

    /**
     * step over the next instruction.
     *
     * This method will put a temporary breakpoint on the instruction
     * following the current one and then switch the emulation to full speed.
     *
     * @throws IllegalStateException if this driver has already been destroyed.
     * @see #destroy()
     */
    public void stepOver()
    {
        runOnThread(driver -> addBreakpoint(new Breakpoint(driver.emulator.pc + 2, true), true));
        start();
    }

    /**
     * Set emulation speed.
     *
     * @param percentageValue value between 0 and 1
     */
    public void setSpeed(float percentageValue)
    {
        if ( percentageValue < 0f || percentageValue > 1f ) {
            throw new IllegalArgumentException("value must be 0...1");
        }
        float factor = Math.max(0, Math.min(1,percentageValue) );
        final long newDelay = (long) ((1.0f-factor)*MAX_DELAY);
        final long newTickInterval = (long) Math.max(1, factor*MAX_TICK_INTERVAL );
        System.out.println("Setting speed to "+(100.0f*factor)+" % " +
                               "(delay: "+newDelay+
                               ", tick interval: "+newTickInterval+")");
        thread.submit( new Cmd( CmdType.RUN, thread ->
        {
            thread.delay = newDelay;
            thread.tickInterval = newTickInterval;
        }));
    }

    /**
     * Execute a callback on the emulation thread.
     *
     * @param callback callback to execute
     * @throws IllegalStateException if this driver has already been destroyed.
     * @see #destroy()
     */
    public void runOnThread(IDriverCallback callback)
    {
        runOnThread(callback, CmdType.RUN );
    }

    private void runOnThread(IDriverCallback callback, CmdType type)
    {
        Validate.notNull(callback, "callback must not be null");
        if ( Thread.currentThread() == thread )
        {
            callback.invoke(this);
        }
        else
        {
            final CountDownLatch latch = new CountDownLatch(1);
            thread.submit( new Cmd( type, thread ->
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

    public <T> T runOnThreadWithResult(IDriverCallbackWithValue<T> callback)
    {
        Validate.notNull(callback, "callback must not be null");
        if ( Thread.currentThread() == thread )
        {
            return callback.invoke(this);
        }
        final AtomicReference<T> result = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        thread.submit( new Cmd( CmdType.RUN, thread ->
        {
            try
            {
                result.set( callback.invoke(this) );
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
        return result.get();
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

    /**
     * Register a callback to be invoked every X cycles.
     *
     * @param listener
     */
    public void registerTickListener(IDriverCallback listener) {
        Validate.notNull(listener, "listener must not be null");
        this.tickListeners.add( listener );
    }

    /**
     * Unregister a callback to be invoked every X cycles.
     *
     * @param listener
     */
    public void removeTickListener(IDriverCallback listener) {
        Validate.notNull(listener, "listener must not be null");
        this.tickListeners.remove( listener );
    }

    /**
     * Registers a callback to be invoked on every emulation state change.
     *
     * @param listener
     */
    public void registerStateListener(IStateListener listener) {
        Validate.notNull(listener, "listener must not be null");
        this.stateListeners.add( listener );
    }

    /**
     * Unregister a callback to be invoked on every emulation state change.
     *
     * @param listener
     */
    public void removeStateListener(IStateListener listener) {
        Validate.notNull(listener, "listener must not be null");
        this.stateListeners.remove( listener );
    }

    /**
     * Returns all permanent and temporary breakpoints currently configured.
     *
     * @return
     */
    public List<Breakpoint> getAllBreakpoints()
    {
        final List<Breakpoint> list = new ArrayList<>();

        runOnThread( ip ->
        {
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

    /**
     * Add a breakpoint.
     *
     * If the same breakpoint already exists, nothing (bad) happens.
     *
     * @param bp breakpoint to add
     * @param enabled breakpoint's initial state.
     */
    public void addBreakpoint(Breakpoint bp,boolean enabled)
    {
        Validate.notNull(bp, "bp must not be null");
        changeState(bp,enabled);
    }

    /**
     * Removes a breakpoint.
     *
     * If the breakpoint is unknown, nothing (bad) happens.
     * @param bp
     */
    public void removeBreakpoint(Breakpoint bp)
    {
        Validate.notNull(bp, "bp must not be null");
        runOnThread( ip -> {
            enabledBreakpoints.remove( bp );
            disabledBreakpoints.remove( bp );
            invokeBreakpointChangeListeners();
        }, CmdType.CHANGE_BREAKPOINTS);
    }

    /**
     * Adds a new breakpoint or removes it if it is already registered.
     *
     * If the breakpoint has not been added, nothing bad happens.
     * @param bp
     */
    public void toggle(Breakpoint bp)
    {
        Validate.notNull(bp, "bp must not be null");
        runOnThread(ip ->
        {
            if ( enabledBreakpoints.contains(bp) || disabledBreakpoints.contains(bp))
            {
                enabledBreakpoints.remove(bp);
                disabledBreakpoints.remove(bp);
            } else {
                enabledBreakpoints.add(bp);
            }
            invokeBreakpointChangeListeners();
        }, CmdType.CHANGE_BREAKPOINTS);
    }

    /**
     * Check whether a given breakpoint is enabled, disabled or unknown.
     *
     * @param bp
     * @return true/false if the breakpoint is enabled/disabled, <code>NULL</code> if the breakpoint is not
     * known.
     */
    public Boolean isEnabled(Breakpoint bp)
    {
        final Boolean[] result = new Boolean[1];
        runOnThread(driver ->
        {
            synchronized (result)
            {
                if (enabledBreakpoints.contains(bp))
                {
                    result[0] = Boolean.TRUE;
                }
                else if (disabledBreakpoints.contains(bp))
                {
                    result[0] = Boolean.FALSE;
                }
            }
        });
        synchronized (result)
        {
            return result[0];
        }
    }

    /**
     * Change the state of a breakpoint.
     *
     * If the breakpoint has not been added yet, it will be after this method returned.
     *
     * @param bp
     * @param enable
     */
    public void changeState(Breakpoint bp,boolean enable)
    {
        runOnThread( driver ->
        {
            if ( enable )
            {
                disabledBreakpoints.remove( bp );
                enabledBreakpoints.add(bp);
            } else {
                enabledBreakpoints.remove( bp );
                disabledBreakpoints.add( bp );
            }
            invokeBreakpointChangeListeners();
        }, CmdType.CHANGE_BREAKPOINTS);
    }

    public void addShutdownListener(Runnable r)
    {
        Validate.notNull( r, "r must not be null" );
        this.shutdownListeners.add( r );
    }

    /**
     * Add breakpoint change listener.
     *
     * @param listener listener to be invoked whenever configured breakpoints are changed (added/removed etc.)
     */
    public void addBreakpointChangeListener(IDriverCallback listener)
    {
        Validate.notNull( listener, "listener must not be null" );
        this.breakpointChangeListeners.add( listener );
    }

    /**
     * Remove breakpoint change listener.
     *
     * @param listener
     * @see #addBreakpointChangeListener(IDriverCallback)
     */
    public void removeBreakpointChangeListener(IDriverCallback listener)
    {
        Validate.notNull( listener, "listener must not be null" );
        this.breakpointChangeListeners.remove( listener );
    }

    /**
     * Tells the emulation that the delay timer finished counting down to zero.
     */
    public void delayTimerTriggered()
    {
        runOnThread(driver -> driver.thread.delayTimerTriggered());
    }

    /**
     * Tells the emulation that a key has been pressed down.
     *
     * @param key key code (0x00...0x0f)
     * @see #keyReleased(int)
     */
    public void keyPressed(int key)
    {
        runOnThread(driver -> driver.thread.keyPressed(key));
    }

    /**
     * Tells the emulation that a key has been released.
     *
     * @param key key code (0x00...0x0f)
     * @see #keyPressed(int)
     */
    public void keyReleased(int key)
    {
        runOnThread(driver -> driver.thread.keyReleased(key));
    }

    private void invokeBreakpointChangeListeners()
    {
        breakpointChangeListeners.forEach( l ->
        {
            try
            {
                l.invoke( EmulatorDriver.this );
            }
            catch(Exception e)
            {
                System.err.println("Breakpoint change listener "+l+" threw exception.");
                e.printStackTrace();
            }
        } );
    }
}