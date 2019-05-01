package de.codesourcery.chip8;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

public class Emulator extends JFrame
{
    private final List<MyFrame> windows;
    private final Controller controller;

    public Emulator(Controller controller)
    {
        Validate.notNull( controller, "controller must not be null" );
        this.controller = controller;
        windows = createFrames();
        final JDesktopPane desktop = new JDesktopPane();
        windows.forEach( win ->
        {
            System.out.println("Window: "+win.getTitle());
            win.tick( controller );
            win.setPreferredSize( new Dimension(200,100) );
            win.setSize( new Dimension(200,100) );
            win.setVisible( true );
            win.setLocation( 0,0 );
            desktop.add( win );
        });
        setContentPane( desktop );
        setPreferredSize( new Dimension(640,400) );
        pack();
        setLocationRelativeTo( null );
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        setVisible( true );
    }

    protected abstract class MyFrame extends JInternalFrame implements Controller.ITickListener, Controller.IStateListener
    {
        public MyFrame(String name,boolean needsTick,boolean needsState)
        {
            super(name,true,true,true);
            if ( needsTick ) {
                controller.registerTickListener( this );
            }
            if ( needsState ) {
                controller.registerStateListener( this );
            }
            this.setDefaultCloseOperation( JInternalFrame.DISPOSE_ON_CLOSE );
        }

        @Override
        public void dispose()
        {
            controller.removeTickListener( this );
            controller.removeStateListener( this );
            super.dispose();
        }

        protected final String hexByte(int value) {
            return "0x" + StringUtils.leftPad( Integer.toString( value, 16 ), 2, '0' );
        }

        protected final String hexWord(int value) {
            return "0x" + StringUtils.leftPad( Integer.toString( value, 16 ), 4, '0' );
        }

        @Override
        public void tick(Controller controller) { }

        @Override
        public void stateChanged(Controller controller, boolean newState) { }
    }

    private List<MyFrame> createFrames()
    {
        final List<MyFrame> result = new ArrayList<>();
        result.add( createScreenView() );
        result.add( createButtonsView() );
        result.add( createDisasmView() );
        result.add( createCPUView() );
        return result;
    }

    private MyFrame createScreenView()
    {
        return new MyFrame("Screen",false,false ) {

            {
                final Panel p = new Panel( controller.interpreter.screen,
                        controller.interpreter.keyboard );
                getContentPane().add( p );
                new Timer(16,ev ->
                {
                    if ( controller.interpreter.screen.checkChanged() )
                    {
                        p.draw( controller.interpreter.screen );
                        p.repaint();
                        Toolkit.getDefaultToolkit().sync();
                    }
                }).start();
            }
        };
    }

    private MyFrame createButtonsView()
    {
        return new MyFrame("Buttons",false,true) {

            private final JButton startButton=new JButton("Start");
            private final JButton resetButton=new JButton("Reset");
            private final JButton stepButton=new JButton("Step");
            private final JButton stopButton=new JButton("Stop");

            {
                getContentPane().setLayout( new FlowLayout() );
                getContentPane().add( startButton );
                getContentPane().add( stepButton );
                getContentPane().add( stopButton );
                getContentPane().add( resetButton );

                stopButton.setEnabled( false );
                startButton.addActionListener( ev -> controller.start() );
                stopButton.addActionListener( ev -> controller.stop() );
                stepButton.addActionListener( ev -> controller.step() );
                resetButton.addActionListener( ev -> controller.reset() );
            }

            @Override
            public void stateChanged(Controller controller, boolean isRunning)
            {
                SwingUtilities.invokeLater(  () ->
                {
                    startButton.setEnabled( !isRunning );
                    stopButton.setEnabled( isRunning );
                    stepButton.setEnabled( !isRunning );
                });
            }
        };
    }

    private MyFrame createCPUView()
    {
        return new MyFrame("CPU",true,false) {

            private final StringBuilder buffer = new StringBuilder();
            private final JTextArea area = new JTextArea();

            {
                area.setEditable( false );
                getContentPane().add( area );
            }

            @Override
            public void tick(Controller controller)
            {
                final Interpreter interpreter = controller.interpreter;
                buffer.setLength(0);
                for ( int i = 0,len=16,inThisRow=0 ; i < len ; i++,inThisRow++ )
                {
                    String regNum = StringUtils.leftPad(Integer.toString(i),2,' ');
                    String reg = "Register "+regNum+": "+hexByte( interpreter.register[i] );
                    buffer.append(reg);
                    if ( ( inThisRow % 4 )== 0 ) {
                        buffer.append("\n");
                    } else {
                        buffer.append("  ");
                    }
                }
                buffer.append("\n\n");
                buffer.append("PC: ").append( hexWord( interpreter.pc ) );
                buffer.append("    Index: ").append( hexWord( interpreter.index ) );
                buffer.append("    SP: ").append( hexByte( interpreter.sp) );
                final String s = buffer.toString();
                SwingUtilities.invokeLater( () -> area.setText( s ) );
            }
        };
    }

    private MyFrame createDisasmView()
    {
        return new MyFrame("Disasm",true,false) {

            private final StringBuilder buffer = new StringBuilder();
            private final JTextArea area = new JTextArea();

            {
                area.setEditable( false );
                getContentPane().add( area );
            }

            @Override
            public void tick(Controller controller)
            {
                final Interpreter interpreter = controller.interpreter;
                int start = Math.max(0,interpreter.pc-8);
                final List<String> lines = Disassembler.disAsm( interpreter.memory,start,16 );
                buffer.setLength(0);
                for (int i = 0, linesSize = lines.size(); i < linesSize; i++,start+=2)
                {
                    if ( start != interpreter.pc ) {
                        buffer.append("____");
                    } else {
                        buffer.append(">>> ");
                    }
                    buffer.append( lines.get( i ) );
                }
                final String s = buffer.toString();
                SwingUtilities.invokeLater( () -> area.setText( s ) );
            }
        };
    }
}