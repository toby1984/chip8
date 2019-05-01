package de.codesourcery.chip8;

import org.apache.commons.lang3.Validate;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import java.awt.HeadlessException;
import java.util.ArrayList;
import java.util.List;

public class Emulator extends JFrame
{
    private final List<MyFrame> windows;
    private final Interpreter interpreter;

    public Emulator(Interpreter interpreter) throws HeadlessException
    {
        Validate.notNull( interpreter, "interpreter must not be null" );
        this.interpreter = interpreter;
        final JDesktopPane desktop = new JDesktopPane();
        windows = createFrames();
        windows.forEach(desktop::add);
        setContentPane( desktop );
        pack();
        setLocationRelativeTo( null );
        setVisible( true );
    }

    protected abstract class MyFrame extends JInternalFrame {

        public MyFrame(String name) {
            super(name);
        }

        public abstract void tick(Interpreter interpreter);
    }

    private List<MyFrame> createFrames()
    {
        final List<MyFrame> result = new ArrayList<>();

        // CPU state frame
        result.add( new MyFrame("CPU") {

        });
        return result;
    }

    public void tick(Interpreter interpreter)
    {
        windows.forEach( w -> w.tick( interpreter ) );
    }
}
