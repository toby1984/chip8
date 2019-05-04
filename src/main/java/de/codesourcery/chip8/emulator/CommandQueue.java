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

/**
 * Helper class that helps passing commands to the emulation thread as well as
 * completely suspending the thread while waiting for external events like key presses
 * or the delay timer.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class CommandQueue
{
    private final Object QUEUE_LOCK = new Object();

    // bit mask indicating the conditions the emulation thread is waiting for
    private int waitFlags; // no need for synchronization as this field is
    // only accessed by the ControllerThread

    // @GuardedBy( LOCK );
    public final List<EmulatorDriver.Cmd> queue = new ArrayList<>();

    public long totalCycleCount;
    public long lastTimestamp;

    /**
     * Polls the command queue for the next command to process, blocking if the queue is empty and wait flags are set.
     *
     * This method will immediately return the next command if the queue is
     * <b>not</b> empty.
     * If the queue is empty and no wait flags are active, this method will return <code>NULL</code>.
     * If the queue is empty and wait flags are active, this method will block until either all
     * wait flags are cleared or a command gets enqueued.
     *
     * @return command to execute or <code>NULL</code> if the queue was empty and no wait flags were active or
     * <code>NULL</code> if the queue was empty and a condition the emulation was waiting for got satisfied
     */
    EmulatorDriver.Cmd poll()
    {
        synchronized (QUEUE_LOCK)
        {
            if ( ! queue.isEmpty() )
            {
                return queue.remove(0);
            }
            return isWaiting() ? take() : null;
        }
    }

    void add(EmulatorDriver.Cmd cmd)
    {
        Validate.notNull(cmd, "cmd must not be null");
        synchronized (QUEUE_LOCK)
        {
            queue.add(cmd);
            QUEUE_LOCK.notifyAll();
        }
    }

    /**
     * Returns wether the emulation thread should wait for an external condition.
     * @return
     */
    boolean isWaiting()
    {
        return waitFlags != 0;
    }

    /**
     * Check whether the emulation thread should wait for a given condition.
     *
     * @param flag flag to checik
     * @return
     * @see #isWaiting()
     */
    boolean isSet(int flag)
    {
        return (waitFlags & flag) != 0;
    }

    /**
     * Sets the wait flag the emulation thread should wait for.
     *
     * @param flag
     * @see #isWaiting()
     */
    public void set(int flag)
    {
        setWaitFlags( waitFlags | flag );
    }

    /**
     * Clears a wait flag.
     *
     * @param flag
     * @see #isWaiting()
     */
    public void clear(int flag) {
        setWaitFlags( waitFlags & ~flag );
    }

    /**
     * Sets the wait flags to a given value.
     * @param value
     */
    void setWaitFlags(int value)
    {
        waitFlags = value;
        synchronized (QUEUE_LOCK)
        {
            QUEUE_LOCK.notifyAll();
        }
    }

    /**
     * Polls the command queue for the next command to process, blocking if the queue is empty or wait flags are set.
     *
     * This method will immediately return the next command if the queue is
     * <b>not</b> empty.
     * If the queue is empty and no wait flags are active, this method will return <code>NULL</code>.
     * If the queue is empty and wait flags are active, this method will block until either all
     * wait flags are cleared or a command gets enqueued.
     *
     * @return command to execute or <code>NULL</code> if the queue was empty and a
     * condition the emulation was waiting for got satisfied
     * @see #isWaiting()
     */
    EmulatorDriver.Cmd take()
    {
        synchronized (QUEUE_LOCK)
        {
            while (queue.isEmpty())
            {
                boolean wasWaiting = waitFlags != 0;
                try
                {
                    QUEUE_LOCK.wait();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                    continue;
                }
                if ( wasWaiting && waitFlags == 0 ) // woke up
                {
                    if ( EmulatorDriver.PRINT_CYCLES_PER_SECOND )
                    {
                        // start counting cycles again since it makes
                        // no sense to track cycles per second while
                        // the thread state was SLEEPING.
                        totalCycleCount = 0;
                        lastTimestamp = System.currentTimeMillis();
                    }
                    return queue.isEmpty() ? null : queue.remove(0);
                }
                if ( ! queue.isEmpty() )
                {
                    return queue.remove(0);
                }
            }
            return queue.remove(0);
        }
    }

    public void reset()
    {
        waitFlags = 0;
    }
}