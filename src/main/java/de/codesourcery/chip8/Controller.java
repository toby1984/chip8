package de.codesourcery.chip8;

import javax.swing.SwingUtilities;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class Controller
{
    public final Interpreter interpreter;

    @FunctionalInterface
    public interface ITickListener {
        void tick(Controller controller);
    }

    @FunctionalInterface
    public interface IStateListener
    {
        void stateChanged(Controller controller, boolean newState);
    }

    private final BlockingQueue<ITickListener> tickListeners =
            new ArrayBlockingQueue<>(100);

    // @GuardedBy( listeners )
    private final BlockingQueue<IStateListener> stateListeners =
            new ArrayBlockingQueue<>(100);

    private final ControllerThread thread = new ControllerThread();

    public enum CmdType {
        START,STOP,STEP,RESET
    }

    private static final class Cmd
    {
        public final CountDownLatch latch=new CountDownLatch(1);
        public final CmdType type;

        private Cmd(CmdType type) {
            this.type = type;
        }

        public void ack() {
            latch.countDown();
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

        public double dummyValue = 123;

        public void submit(Cmd cmd)
        {
            cmdQueue.add( cmd );
            try
            {
                cmd.latch.await();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
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
            while( true)
            {
                final Cmd cmd;
                if ( running )
                {
                    cmd = cmdQueue.peek();
                    if ( cmd != null ) {
                        cmdQueue.remove();
                    }
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
                    cmd.ack();
                    switch(cmd.type)
                    {
                        case RESET:
                            running = false;
                            interpreter.reset();
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
                    l.stateChanged( Controller.this, newState );
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
                    l.tick( Controller.this );
                }
                catch(Exception e)
                {
                    System.err.println("Tick listener "+l+" threw exception.");
                    e.printStackTrace();
                }
            } );
        }
    }

    public Controller(Interpreter interpreter)
    {
        this.interpreter = interpreter;
        thread.setDaemon( true );
        thread.setName("controller-thread");
        thread.start();
    }

    public void start() {
        thread.submit( new Cmd(CmdType.START) );
    }

    public void reset() {
        thread.submit( new Cmd(CmdType.RESET) );
    }

    public void stop() {
        thread.submit( new Cmd(CmdType.STOP) );
    }

    public void step() {
        thread.submit( new Cmd(CmdType.STEP) );
    }

    public void registerTickListener(ITickListener listener) {
        this.tickListeners.add( listener );
    }

    public void removeTickListener(ITickListener listener) {
        this.tickListeners.remove( listener );
    }

    public void registerStateListener(IStateListener listener) {
        this.stateListeners.add( listener );
    }

    public void removeStateListener(IStateListener listener) {
        this.stateListeners.remove( listener );
    }
}