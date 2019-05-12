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
package de.codesourcery.chip8.ui;

import de.codesourcery.chip8.Disassembler;
import de.codesourcery.chip8.asm.Assembler;
import de.codesourcery.chip8.asm.CompilationMessages;
import de.codesourcery.chip8.asm.ExecutableWriter;
import de.codesourcery.chip8.asm.ExpressionEvaluator;
import de.codesourcery.chip8.asm.ISymbolResolver;
import de.codesourcery.chip8.asm.Identifier;
import de.codesourcery.chip8.asm.SymbolTable;
import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.CommentNode;
import de.codesourcery.chip8.asm.ast.DirectiveNode;
import de.codesourcery.chip8.asm.ast.IdentifierNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.ast.TextNode;
import de.codesourcery.chip8.asm.ast.TextRegion;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import de.codesourcery.chip8.emulator.Breakpoint;
import de.codesourcery.chip8.emulator.Emulator;
import de.codesourcery.chip8.emulator.EmulatorDriver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDesktopPane;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The application's UI.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class MainFrame extends JFrame
{
    public enum ConfigKey
    {
        // data
        GLOBAL("global","Global Configuration",false),
        // top-level window
        MAINFRAME("mainframe","Main Frame", false),
        // internal frames
        ASM("asm","Assembler"),
        DISASM("disasm","Disassembly"),
        CPU("cpu","CPU Registers"),
        BUTTONS("buttons","Buttons"),
        SCREEN("screen","Screen"),
        SPRITE_VIEW("sprite_view","Sprites"),
        MEMORY("memory","Memory"),
        BREAKPOINTS( "breakpoints", "Breakpoints");

        private final String id;
        public final boolean isInternalFrame;
        public final String displayName;

        ConfigKey(String id,String displayName) {
            this(id,displayName,true);
        }

        ConfigKey(String id,String displayName, boolean isInternalFrame)
        {
            this.id = id;
            this.isInternalFrame = isInternalFrame;
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            // do NOT change toString() ; relied upon by config serialization
            return id;
        }
    }

    private Timer swingTimer;

    public interface IConfigurationProvider
    {
        Properties load();
        void save();
    }

    private final Map<ConfigKey,MyFrame> windows = new HashMap<>();
    private final EmulatorDriver driver;

    private final IConfigurationProvider configProvider;
    private final Properties config;
    private final JDesktopPane desktop = new JDesktopPane();

    public MainFrame(EmulatorDriver driver, IConfigurationProvider configProvider)
    {
        Validate.notNull( driver, "controller must not be null" );
        Validate.notNull(configProvider, "configProvider must not be null");
        this.driver = driver;
        this.configProvider = configProvider;
        this.config = configProvider.load();
        Stream.of( ConfigKey.values() ).filter( x -> x.isInternalFrame ).forEach( this::toggleVisibility );
        setJMenuBar( createMenuBar() );
        setContentPane( desktop );
        setSize( new Dimension(640,400) );
        setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        Configuration.applyWindowState(config, ConfigKey.MAINFRAME, MainFrame.this);
        setVisible( true );
        setLocationRelativeTo( null );
        addWindowListener(new WindowAdapter() {@Override public void windowClosing(WindowEvent e) { quit(); } } );
    }

    protected abstract class MyFrame extends JInternalFrame implements EmulatorDriver.IDriverCallback, EmulatorDriver.IStateListener
    {
        public final ConfigKey configKey;
        private final boolean needsTick;
        private final boolean needsState;

        public MyFrame(String title, ConfigKey configKey, boolean needsTick, boolean needsState)
        {
            super(title,true,true,true);
            this.needsTick = needsTick;
            this.needsState = needsState;
            this.setDefaultCloseOperation( JInternalFrame.DISPOSE_ON_CLOSE );
            this.configKey = configKey;
            initialize( driver );
            configure(this);
        }

        protected void initialize(EmulatorDriver driver)
        {
            if ( needsTick ) {
                driver.registerTickListener( this );
            }
            if ( needsState ) {
                driver.registerStateListener( this );
            }
        }

        protected final void configure(Component component) {
            component.setFont(new Font("monospaced", Font.PLAIN, 12));
        }

        @Override
        public final void dispose()
        {
            driver.removeTickListener( this );
            driver.removeStateListener( this );
            try {
                onDispose();
            }
            finally
            {
                super.dispose();
            }
        }

        protected void onDispose() {
        }

        protected final String hexByte(int value) {
            return "0x" + StringUtils.leftPad( Integer.toString( value, 16 ), 2, '0' );
        }

        protected final String hexWord(int value) {
            return "0x" + StringUtils.leftPad( Integer.toString( value, 16 ), 4, '0' );
        }

        @Override
        public void invoke(EmulatorDriver controller) { }

        @Override
        public void stateChanged(EmulatorDriver controller, EmulatorDriver.Reason reason) { }
    }

    private MyFrame createInternalFrame(ConfigKey key)
    {
        switch( key ) {
            case ASM:
                return createAsmView();
            case DISASM:
                return createDisasmView();
            case CPU:
                return createCPUView();
            case BUTTONS:
                return createButtonsView();
            case SCREEN:
                return createScreenView();
            case SPRITE_VIEW:
                return createSpriteView();
            case MEMORY:
                return createMemoryView();
            case BREAKPOINTS:
                return createBreakpointsView();
            default:
                throw new IllegalArgumentException("Unhandled key "+key);
        }
    }

    private MyFrame createBreakpointsView()
    {
        return new MyFrame( "Breakpoints", ConfigKey.BREAKPOINTS, false, false )
        {
            private final Object LOCK = new Object();

            // @GuardedBy( LOCK )
            private List<Breakpoint> breakpoints = new ArrayList<>();

            final class MyTableModel extends DefaultTableModel {

                @Override
                public int getRowCount()
                {
                    synchronized( LOCK ) {
                        return breakpoints.size();
                    }
                }

                @Override public int getColumnCount() { return 2; }

                public void dataChanged() { fireTableDataChanged(); }

                @Override
                public String getColumnName(int columnIndex)
                {
                    switch(columnIndex) {
                        case 0:
                            return "Address";
                        case 1:
                            return "Enabled";
                        default:
                            throw new IllegalArgumentException( "Out of range: "+columnIndex );
                    }
                }

                @Override
                public Class<?> getColumnClass(int columnIndex)
                {
                    switch(columnIndex) {
                        case 0:
                            return String.class;
                        case 1:
                            return Boolean.class;
                        default:
                            throw new IllegalArgumentException( "Out of range: "+columnIndex );
                    }
                }

                @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return columnIndex==1; }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex)
                {
                    synchronized (LOCK)
                    {
                        final Breakpoint value = breakpoints.get( rowIndex );
                        switch(columnIndex) {
                            case 0:
                                return "0x" + StringUtils.leftPad( Integer.toHexString( value.address ), 3, '0' );
                            case 1:
                                final Boolean isEnabled = driver.runOnThreadWithResult( d -> d.isEnabled( value ) );
                                return isEnabled == null ? false : isEnabled;
                            default:
                                throw new IllegalArgumentException( "Out of range: "+columnIndex );
                        }
                    }
                }

                @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
                    if (columnIndex==1)
                    {
                        final Breakpoint bp;
                        synchronized (LOCK)
                        {
                            bp = breakpoints.get( rowIndex );
                        }
                        driver.runOnThread( d -> d.changeState( bp , (Boolean) aValue ) );
                    }
                }
            };

            private MyTableModel tableModel = new MyTableModel();

            private JTable table = new JTable(tableModel);

            {
                getContentPane().setLayout( new BorderLayout() );
                getContentPane().add( new JScrollPane( table ), BorderLayout.CENTER );
                table.addKeyListener( new KeyAdapter()
                {
                    @Override
                    public void keyReleased(KeyEvent e)
                    {
                        if ( e.getKeyCode() == KeyEvent.VK_DELETE )
                        {
                            final int idx = table.getSelectedRow();
                            if ( idx >= 0 )
                            {
                                final Breakpoint bp;
                                synchronized (LOCK)
                                {
                                    bp = breakpoints.get( idx );
                                }
                                driver.runOnThread( d -> d.removeBreakpoint( bp ) );
                            }
                        }
                    }
                } );
            }

            volatile EmulatorDriver.IDriverCallback bpListener;

            @Override
            protected void initialize(EmulatorDriver driver)
            {
                super.initialize( driver );
                bpListener = emulatorDriver ->
                {
                    synchronized (LOCK)
                    {
                        final List<Breakpoint> tmp = emulatorDriver.getAllBreakpoints();
                        tmp.removeIf( t -> t.isTemporary );
                        tmp.sort( Comparator.comparingInt( x -> x.address ) );
                        breakpoints = tmp;
                    }
                    SwingUtilities.invokeLater( tableModel::dataChanged );
                };
                driver.addBreakpointChangeListener( bpListener );
            }

            @Override
            protected void onDispose()
            {
                driver.removeBreakpointChangeListener( bpListener );
            }
        };
    }

    private MyFrame createMemoryView()
    {
        return new MyFrame("Memory", ConfigKey.MEMORY, true,false )
        {
            public static final int BYTES_TO_DUMP = 128;
            public static final int BYTES_PER_ROW = 16;

            private final JTextArea dump = new JTextArea();
            private final JTextField address = new JTextField("pc");

            private volatile String expression = "pc";

            {
                dump.setEditable(false);
                configure(dump);
                getContentPane().setLayout(new BorderLayout());
                getContentPane().add( address, BorderLayout.NORTH);
                getContentPane().add( dump, BorderLayout.CENTER );
                address.addActionListener(ev ->
                {
                    expression = address.getText();
                    driver.runOnThread(this);
                });

                final KeyAdapter adapter = new KeyAdapter()
                {
                    @Override
                    public void keyReleased(KeyEvent e)
                    {
                        Function<Integer, Integer> func = null;
                        switch (e.getKeyCode())
                        {
                            case KeyEvent.VK_UP:
                                func = adr -> adr - BYTES_PER_ROW;
                                break;
                            case KeyEvent.VK_PAGE_UP:
                                func = adr -> adr - BYTES_TO_DUMP / 2;
                                break;
                            case KeyEvent.VK_DOWN:
                                func = adr -> adr + BYTES_PER_ROW;
                                break;
                            case KeyEvent.VK_PAGE_DOWN:
                                func = adr -> adr + BYTES_TO_DUMP / 2;
                                break;
                        }
                        if (func != null)
                        {
                            Integer current = driver.runOnThreadWithResult(ip -> evaluate(expression));
                            if (current != null)
                            {
                                current = func.apply(current) % driver.emulator.memory.getSizeInBytes();
                                expression = "0x" + Integer.toHexString(current);
                                address.setText(expression);
                                refresh();
                            }
                        }
                    }
                };
                dump.addKeyListener(adapter);
            }

            private void refresh() {
                driver.runOnThread(this);
            }

            @Override
            public void invoke(EmulatorDriver controller)
            {
                final Integer adr = evaluate(expression);
                if ( adr != null )
                {
                    final String text = controller.emulator.memory.dump(
                            adr % controller.emulator.memory.getSizeInBytes(),
                            BYTES_TO_DUMP,
                            BYTES_PER_ROW);
                    SwingUtilities.invokeLater(() -> dump.setText(text));
                }
            }
        };
    }

    private MyFrame createSpriteView()
    {
        return new MyFrame("Sprites", ConfigKey.SPRITE_VIEW, true, false)
        {
            private static final int BYTES_TO_DISPLAY = 16;

            private JTextField textfield = new JTextField("0x00");

            private volatile String expression = "0x00";

            private final byte[] data = new byte[BYTES_TO_DISPLAY];

            private final JPanel panel = new JPanel() {

                @Override
                protected void paintComponent(Graphics g)
                {
                    super.paintComponent(g);

                    synchronized (data)
                    {
                        final int height = getHeight() < BYTES_TO_DISPLAY ? getHeight() : BYTES_TO_DISPLAY;
                        int w = getWidth()/8;
                        int h = (int) (getHeight() / (float) height);
                        int boxSize = Math.min(w,h);
                        int y0 = 0;
                        for (int y = 0, ptr = 0 ; y < height ; y++ )
                        {
                            int mask = 0b1000_0000;
                            int value = data[ptr++];
                            for (int x = 0; x < 8; x++,mask >>>=1 )
                            {
                                Color color;
                                if ( ( value & mask) != 0 ) {
                                    color = Color.WHITE;
                                } else {
                                    color = Color.BLACK;
                                }
                                g.setColor(color);
                                g.fillRect(x*boxSize,y0+(y*boxSize),boxSize,boxSize);
                            }
                        }
                    }
                }
            };

            @Override
            public void invoke(EmulatorDriver controller)
            {
                Integer adr = evaluate(expression);
                if ( adr != null )
                {
                    synchronized (data)
                    {
                        controller.emulator.memory.read(adr, BYTES_TO_DISPLAY, data);
                    }
                }
            }

            private void refresh() {
                driver.runOnThread(this);
                panel.repaint();
            }

            {
                getContentPane().setLayout(new BorderLayout());
                getContentPane().add(textfield, BorderLayout.NORTH );
                getContentPane().add(panel, BorderLayout.CENTER);
                textfield.addActionListener( ev ->
                {
                    expression = textfield.getText();
                    refresh();
                });

                final KeyAdapter adapter = new KeyAdapter()
                {
                    @Override
                    public void keyReleased(KeyEvent e)
                    {
                        Function<Integer, Integer> func = null;
                        switch (e.getKeyCode())
                        {
                            case KeyEvent.VK_UP:
                                func = adr -> adr - 1;
                                break;
                            case KeyEvent.VK_PAGE_UP:
                                func = adr -> adr - BYTES_TO_DISPLAY/2;
                                break;
                            case KeyEvent.VK_DOWN:
                                func = adr -> adr + 1;
                                break;
                            case KeyEvent.VK_PAGE_DOWN:
                                func = adr -> adr + BYTES_TO_DISPLAY/2;
                                break;
                        }
                        if (func != null)
                        {
                            Integer current = driver.runOnThreadWithResult(ip -> evaluate(expression));
                            if (current != null)
                            {
                                current = func.apply(current) % driver.emulator.memory.getSizeInBytes();
                                expression = "0x" + Integer.toHexString(current);
                                textfield.setText(expression);
                                refresh();
                            }
                        }
                    }
                };
                panel.setFocusable(true);
                panel.addKeyListener(adapter);
            }
        };
    }

    private MyFrame createScreenView()
    {
        return new MyFrame("Screen", ConfigKey.SCREEN, false,false ) {

            {
                final ScreenPanel p = new ScreenPanel( driver );
                getContentPane().add( p );
                swingTimer = new Timer(16, ev ->
                {
                    final boolean screenChanged = driver.runOnThreadWithResult(driver ->
                    {
                        if (driver.emulator.screen.hasChanged())
                        {
                            p.draw(driver.emulator.screen);
                            return true;
                        }
                        return false;
                    });
                    if ( screenChanged )
                    {
                        p.repaint();
                        Toolkit.getDefaultToolkit().sync();
                    }
                });
                swingTimer.start();
            }
        };
    }

    private MyFrame createButtonsView()
    {
        return new MyFrame("Buttons", ConfigKey.BUTTONS, false,true) {

            private final JButton startButton=new JButton("Start");
            private final JButton resetButton=new JButton("Reset");
            private final JButton stepButton=new JButton("Step");
            private final JButton stepOverButton=new JButton("Step over");
            private final JButton stopButton=new JButton("Stop");
            private final JButton loadButton=new JButton("Load");
            private final JSlider speed = new JSlider(0,1000,500);

            {
                getContentPane().setLayout( new FlowLayout() );
                getContentPane().add( startButton );
                getContentPane().add( stepButton );
                getContentPane().add( stepOverButton );
                getContentPane().add( stopButton );
                getContentPane().add( resetButton );
                getContentPane().add( loadButton );
                getContentPane().add( speed );

                stopButton.setEnabled( false );
                speed.addChangeListener( new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e)
                    {
                        if ( ! speed.getValueIsAdjusting() )
                        {
                            final float factor = speed.getValue()/1000f;
                            driver.setSpeed( factor );
                        }
                    }
                } );
                startButton.addActionListener( ev -> driver.start() );
                stopButton.addActionListener( ev -> driver.stop() );
                stepButton.addActionListener( ev -> driver.step() );
                stepOverButton.addActionListener( ev -> driver.stepOver() );
                resetButton.addActionListener( ev -> driver.reset() );
                loadButton.addActionListener( ev ->
                {
                    final Configuration serializer = Configuration.of(config);
                    final File file = serializer.getLastBinary();
                    final File selected = selectFileOpen(file);
                    if ( selected != null )
                    {
                        driver.stop();

                        serializer.setLastBinary(selected);
                        configProvider.save();

                        driver.runOnThread(driver -> {
                            driver.emulator.setResetHook(interpreter ->
                            {
                                try
                                {
                                    System.out.println("Loading executable from "+selected.getAbsolutePath());
                                    final int bytes = interpreter.memory.load( new FileInputStream(selected), 0x200 );
                                    System.out.println("Loaded "+bytes+" bytes from "+selected.getAbsolutePath());
                                }
                                catch (IOException e)
                                {
                                    System.err.println("Failed to load binary from "+selected.getAbsolutePath());
                                    e.printStackTrace();
                                }
                            });
                        });

                        driver.reset();
                    }
                });
            }

            @Override
            public void stateChanged(EmulatorDriver controller, EmulatorDriver.Reason reason)
            {
                SwingUtilities.invokeLater(  () ->
                {
                    final boolean isRunning = reason == EmulatorDriver.Reason.STARTED;
                    startButton.setEnabled( !isRunning );
                    stopButton.setEnabled( isRunning );
                    stepButton.setEnabled( !isRunning );
                    stepOverButton.setEnabled( !isRunning );
                });
            }
        };
    }

    private MyFrame createCPUView()
    {
        return new MyFrame("CPU", ConfigKey.CPU, true,false) {

            private final StringBuilder buffer = new StringBuilder();
            private final JTextArea area = new JTextArea();

            {
                area.setEditable( false );
                getContentPane().add( area );
                configure(area);
            }

            @Override
            public void invoke(EmulatorDriver controller)
            {
                final Emulator interpreter = controller.emulator;
                buffer.setLength(0);
                buffer.append("PC: ").append( hexWord( interpreter.pc ) );
                buffer.append("    Index: ").append( hexWord( interpreter.index ) );
                buffer.append("    SP: ").append( hexByte( interpreter.sp) );
                buffer.append("\n\n");

                for ( int reg = 0 ; reg < 4 ; reg++)
                {
                    int num = reg;
                    for (int i = 4 ; i > 0 ; i--, num+=4 )
                    {
                        String regNum = StringUtils.leftPad(Integer.toString(num), 2, ' ');
                        String sReg = "Register " + regNum + ": " + hexByte(interpreter.register[num]);
                        buffer.append(sReg);
                        if (i>1)
                        {
                            buffer.append("  ");
                        }
                    }
                    buffer.append("\n");
                }


                final String s = buffer.toString();
                SwingUtilities.invokeLater( () -> area.setText( s ) );
            }
        };
    }

    private MyFrame createDisasmView()
    {
        return new MyFrame("Disasm", ConfigKey.DISASM, true,true) {

            private static final int WORDS_TO_DISASSEMBLE = 16;

            private volatile EmulatorDriver.IDriverCallback bpListener;

            // @GuardedBy( lines )
            private boolean userProvidedAddress;

            // @GuardedBy( lines )
            private int startAddress;

            // @GuardedBy( lines )
            private int currentPC;

            // @GuardedBy( lines )
            private final List<String> lines = new ArrayList<>();

            @Override
            public void stateChanged(EmulatorDriver controller, EmulatorDriver.Reason reason)
            {
                synchronized (lines) {
                    userProvidedAddress = false;
                }
            }

            @Override
            protected void initialize(EmulatorDriver driver)
            {
                super.initialize( driver );
                bpListener = d -> refresh();
                driver.addBreakpointChangeListener( bpListener);
            }

            @Override
            protected void onDispose()
            {
                driver.removeBreakpointChangeListener( bpListener );
            }

            private void toggleBreakpoint(int address)
            {
                driver.toggle(new Breakpoint(address,false) );
            }

            private void refresh()
            {
                driver.runOnThread( this );
            }

            private final JPanel panel = new JPanel() {

                {
                    addMouseListener(new MouseAdapter()
                    {
                        @Override
                        public void mouseClicked(MouseEvent e)
                        {
                            final int h = getFontMetrics(getFont()).getHeight();
                            int row = e.getY()/ h;
                            boolean toggle=false;
                            int bpAddress=0;
                            synchronized(lines)
                            {
                                if (row >= 0 && row < lines.size())
                                {
                                    toggle = true;
                                    bpAddress = startAddress + row * 2;
                                }
                            }
                            if ( toggle ) {
                                toggleBreakpoint(bpAddress);
                            }
                        }
                    });
                    setFocusable(true);
                    addKeyListener(new KeyAdapter()
                    {
                        @Override
                        public void keyReleased(KeyEvent e)
                        {
                            Supplier<Integer> func = null;
                            switch( e.getKeyCode() )
                            {
                                case KeyEvent.VK_PAGE_UP:
                                    func = () -> (startAddress - 2*(WORDS_TO_DISASSEMBLE/2));
                                    break;
                                case KeyEvent.VK_PAGE_DOWN:
                                    func = () -> (startAddress + 2*(WORDS_TO_DISASSEMBLE/2));
                                    break;
                                case KeyEvent.VK_UP:
                                    func = () -> (startAddress - 2);
                                    break;
                                case KeyEvent.VK_DOWN:
                                    func = () -> startAddress+2;
                                    break;
                            }
                            if ( func != null )
                            {
                                synchronized (lines)
                                {
                                    userProvidedAddress = true;
                                    startAddress = func.get() % driver.emulator.memory.getSizeInBytes();
                                }
                                refresh();
                            }
                        }
                    });
                }

                @Override
                protected void paintComponent(Graphics g)
                {
                    super.paintComponent(g);
                    final int fontHeight = g.getFontMetrics().getHeight();
                    int x0 = 0;
                    int y0 = fontHeight;

                    final Map<Integer, Breakpoint> bps = new HashMap<>();
                    driver.runOnThread(emuDriver ->
                    {
                        synchronized(bps)
                        {
                            emuDriver.getAllBreakpoints().stream()
                                    .filter(x -> !x.isTemporary)
                                    .filter( emuDriver::isEnabled )
                                    .forEach(x -> bps.put(x.address, x));
                        }
                    });
                    synchronized (lines)
                    {
                        synchronized (bps)
                        {
                            int address = startAddress;
                            for (String line : lines)
                            {
                                final String pcMarker = address == currentPC ? " >> " : "    ";
                                if (bps.containsKey(address))
                                {
                                    g.drawString("[B]" + pcMarker + line, x0, y0);
                                }
                                else
                                {
                                    g.drawString("[ ]" + pcMarker + line, x0, y0);
                                }
                                y0 += fontHeight;
                                address += 2;
                            }
                        }
                    }
                }
            };

            {
                getContentPane().add( panel );
                configure(panel);
            }

            @Override
            public void invoke(EmulatorDriver controller)
            {
                synchronized(this.lines)
                {
                    final Emulator interpreter = controller.emulator;
                    this.currentPC = interpreter.pc;

                    final int displayStart;
                    if ( userProvidedAddress )
                    {
                        displayStart = startAddress;
                    }
                    else
                    {
                        if (Math.abs(currentPC - startAddress) > (WORDS_TO_DISASSEMBLE-1))
                        {
                            displayStart = Math.max(0, currentPC - WORDS_TO_DISASSEMBLE/2);
                        }
                        else
                        {
                            displayStart = startAddress;
                        }
                    }
                    final List<String> lines = Disassembler.disAsm( interpreter.memory,displayStart,WORDS_TO_DISASSEMBLE );

                    this.startAddress = displayStart;
                    this.lines.clear();
                    this.lines.addAll( lines );
                }
                panel.repaint();
            }
        };
    }

    private MyFrame createAsmView()
    {
        return new MyFrame("Assembler", ConfigKey.ASM, false,false) {

            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            private final JButton compile = new JButton("Compile");
            private final JButton load = new JButton("Load");
            private final JButton save = new JButton("Save");
            private final JButton help = new JButton("Syntax Help");
            private final JButton showASTViewer = new JButton("AST viewer");

            private final JFrame helpFrame = new JFrame("help")
            {
                @Override
                public void setVisible(boolean b)
                {
                    if ( b ) {
                        loadHTML();
                    }
                    super.setVisible( b );
                }

                private void loadHTML()
                {
                    final String PATH = "/asm_help.html";
                    final URL url = getClass().getResource( PATH );
                    if ( url != null )
                    {
                        try
                        {
                            textPane.setPage( url );
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                            new Exception("ASM syntax help file not found in classpath '"+PATH+"'").printStackTrace();
                        }
                    }
                    else
                    {
                        new Exception("ASM syntax help file not found in classpath '"+PATH+"'").printStackTrace();
                    }
                }

                private final JEditorPane textPane = new JEditorPane();

                {
                    setDefaultCloseOperation( JFrame.HIDE_ON_CLOSE );
                    textPane.setEditable( false );
                    textPane.setPreferredSize( new Dimension(640,240 ) );
                    getContentPane().add( new JScrollPane( textPane ) );
                    pack();
                    setLocationRelativeTo( null );

                    textPane.addHyperlinkListener(new HyperlinkListener()
                    {
                        @Override
                        public void hyperlinkUpdate(final HyperlinkEvent event)
                        {
                            if (HyperlinkEvent.EventType.ACTIVATED == event.getEventType())
                            {
                                System.out.println("JEditorPane link click: url='" + event.getURL() + "' description='" + event.getDescription() + "'");
                                String reference = event.getDescription();
                                if (reference != null && reference.startsWith("#")) { // link must start with # to be internal reference
                                    reference = reference.substring(1);
                                    textPane.scrollToReference(reference);
                                }
                            }
                        }
                    });
                }
            };

            private final ASTViewer astViewer = new ASTViewer();
            private final Thread highlightThread;
            private final Object DOC_LOCK = new Object();
            private boolean documentChanged = true;
            private boolean documentChangeListenerEnabled=true;

            private final JTextPane source = new JTextPane();
            private final List<CompilationMessages.CompilationMessage> messages = new ArrayList<>();
            private final JTable table = new JTable(new DefaultTableModel()
            {
                @Override public int getRowCount() { return messages.size(); }

                @Override
                public int getColumnCount() {return 4;}

                @Override
                public String getColumnName(int columnIndex)
                {
                    switch(columnIndex) {
                        case 0: return "Time";
                        case 1: return "Severity";
                        case 2: return "Location";
                        case 3: return "Message";
                    }
                    throw new IllegalArgumentException("Invalid column "+columnIndex);
                }

                @Override public Class<?> getColumnClass(int columnIndex) { return String.class; }
                @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex)
                {
                    switch(columnIndex) {
                        case 0: return formatter.format(messages.get(rowIndex).timestamp );
                        case 1: return messages.get(rowIndex).severity.toString();
                        case 2:
                            {
                                final CompilationMessages.CompilationMessage message = messages.get( rowIndex );
                                if ( message.offset >= 0 )
                                {
                                    Point p = getSourceLocation( message.offset );
                                    return p == null ? "n/a" : "Line " + p.y + ", column " + p.x;
                                }
                                return "n/a";
                            }
                        case 3: return messages.get(rowIndex).message;
                    }
                    throw new IllegalArgumentException("Invalid column "+columnIndex);
                }

                @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) { }
            })
            {

                // JTable constructor...
                {
                    getSelectionModel().addListSelectionListener( new ListSelectionListener()
                    {
                        @Override
                        public void valueChanged(ListSelectionEvent e)
                        {
                            final int idx = e.getFirstIndex();
                            final CompilationMessages.CompilationMessage message =
                                    idx < messages.size() ? messages.get( idx ) : null;
                            if ( message != null && message.offset >= 0 )
                            {
                                source.setCaretPosition( message.offset );
                                source.requestFocus();
                            }
                        }
                    });
                }
            };

            {
                final Configuration globalConf = Configuration.of( config );

                for ( Configuration.HighlightingColors key : Configuration.HighlightingColors.values() ) {
                    globalConf.addStyle( key, document() );
                }

                document().addStyle( "defaultStyle", null );

                configure(source);

                highlightThread = new Thread(() ->
                {
                    while( true )
                    {
                        try {
                            synchronized( DOC_LOCK )
                            {
                                if ( documentChanged )
                                {
                                    documentChanged = false;
                                } else {
                                    DOC_LOCK.wait();
                                    continue;
                                }
                            }
                            Thread.sleep(250);
                            synchronized( DOC_LOCK )
                            {
                                if ( documentChanged ) {
                                    continue;
                                }
                            }
                            // do highlighting
                            SwingUtilities.invokeAndWait(() ->
                            {
                                try
                                {
                                    final String text = source.getText();
                                    if ( text != null )
                                    {
                                        documentChangeListenerEnabled = false;
                                        final Assembler.CompilationContext ctx =
                                                new Assembler.CompilationContext(new ExecutableWriter());
                                        final Parser p = new Parser(new Lexer(new Scanner(text)), ctx);
                                        final ASTNode ast;
                                        try
                                        {
                                            ast = p.parse();
                                        }
                                        catch(Exception e) {
                                            return;
                                        }

                                        astViewer.setAST( ast );

                                        final StyledDocument document = document();
                                        document.setCharacterAttributes(0,text.length(),
                                                document.getStyle( "defaultStyle" ),true );
                                        ast.visitInOrder((node, depth) ->
                                        {
                                            final TextRegion region = node.getRegion();
                                            if (node instanceof InstructionNode)
                                            {
                                                InstructionNode insn = (InstructionNode) node;
                                                System.out.println("Highlighting "+insn.mnemonic+" @ "+insn.getRegion());
                                                document.setCharacterAttributes(
                                                        region.getStartingOffset(),
                                                        region.getLength(),
                                                        Configuration.HighlightingColors.INSTRUCTION.getStyle( document ), true);
                                            } else if ( node instanceof LabelNode || node instanceof IdentifierNode ) {
                                                document.setCharacterAttributes(
                                                        region.getStartingOffset(), region.getLength(),
                                                        Configuration.HighlightingColors.IDENTIFIER.getStyle( document ), true);
                                            } else if ( node instanceof TextNode ) {
                                                document.setCharacterAttributes(
                                                        region.getStartingOffset(), region.getLength(),
                                                        Configuration.HighlightingColors.TEXT.getStyle( document ), true);
                                            } else if ( node instanceof RegisterNode ) {
                                                document.setCharacterAttributes(
                                                        region.getStartingOffset(), region.getLength(),
                                                        Configuration.HighlightingColors.REGISTER.getStyle( document ), true);
                                            } else if ( node instanceof DirectiveNode ) {
                                                document.setCharacterAttributes(
                                                        region.getStartingOffset(), region.getLength(),
                                                        Configuration.HighlightingColors.DIRECTIVE.getStyle( document ), true);
                                            } else if ( node instanceof CommentNode ) {
                                                document.setCharacterAttributes(
                                                        region.getStartingOffset(), region.getLength(),
                                                        Configuration.HighlightingColors.COMMENT.getStyle( document ), true);
                                            }
                                        });
                                    }
                                } finally {
                                    documentChangeListenerEnabled = true;
                                }
                            });
                        } catch (InvocationTargetException| InterruptedException e) { /* can't help it */ }
                    }
                });
                highlightThread.setDaemon(true);
                highlightThread.start();

                source.getStyledDocument().addDocumentListener(new DocumentListener()
                {
                    @Override public void insertUpdate(DocumentEvent e) { documentChanged(); }
                    @Override public void removeUpdate(DocumentEvent e) { documentChanged(); }
                    @Override public void changedUpdate(DocumentEvent e) { documentChanged(); }

                    private void documentChanged()
                    {
                        if ( documentChangeListenerEnabled )
                        {
                            synchronized (DOC_LOCK)
                            {
                                documentChanged = true;
                                DOC_LOCK.notifyAll();
                            }
                        }
                    }
                });

                getContentPane().setLayout( new GridBagLayout());

                showASTViewer.addActionListener(  ev ->
                {
                        if ( astViewer.isVisible() )
                        {
                            astViewer.setVisible( false );
                        }
                        else
                        {
                            astViewer.setVisible( true );
                        }
                });

                help.addActionListener( ev -> {
                    if ( helpFrame.isVisible() ) {
                        helpFrame.setVisible( false );
                    } else {
                        helpFrame.setVisible( true );
                    }
                });

                save.addActionListener( ev -> {

                    final File last = Configuration.of(config).getLastSource();
                    final File file = selectFileSave(last);
                    if ( file != null )
                    {
                        try (FileWriter w = new FileWriter(file) )
                        {
                            final String text = source.getText();
                            w.write(text==null ? "": text );
                            System.out.println("Saved source to "+file.getAbsolutePath());
                            Configuration.of(config).setLastSource(file);
                            configProvider.save();
                        }
                        catch (IOException e)
                        {
                            System.err.println("Failed to load source from "+file.getAbsolutePath());
                            e.printStackTrace();
                        }
                    }
                });

                load.addActionListener(ev ->
                {
                    final File last = Configuration.of(config).getLastSource();
                    final File file = selectFileOpen(last);
                    if ( file != null )
                    {
                        loadSource(file);
                    }
                });
                compile.addActionListener( ev -> compile() );

                final JPanel buttons = new JPanel();
                buttons.setLayout( new FlowLayout() );
                buttons.add( compile );
                buttons.add( load );
                buttons.add( save );
                buttons.add( help );
                buttons.add( showASTViewer );

                GridBagConstraints cnstrs = new GridBagConstraints();
                cnstrs.gridx = 0; cnstrs.gridy = 0;
                cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
                cnstrs.fill = GridBagConstraints.BOTH;
                getContentPane().add( buttons , cnstrs );

                cnstrs = new GridBagConstraints();
                cnstrs.gridx = 0; cnstrs.gridy = 1;
                cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
                cnstrs.weightx = 1; cnstrs.weighty = 0.7;
                cnstrs.fill = GridBagConstraints.BOTH;
                getContentPane().add( new JScrollPane(source), cnstrs );

                cnstrs = new GridBagConstraints();
                cnstrs.gridx = 0; cnstrs.gridy = 2;
                cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
                cnstrs.weightx = 1; cnstrs.weighty = 0.2;
                cnstrs.fill = GridBagConstraints.BOTH;
                getContentPane().add( new JScrollPane(table), cnstrs );

                final File last = Configuration.of(config).getLastSource();
                if ( last != null ) {
                    loadSource(last);
                }
            }

            public Point getSourceLocation(int offset)
            {
                final String src = source.getText();
                int line = 1;
                int column = 1;
                for ( int i = 0, currentOffset = -1 ; i < src.length() ; i++ )
                {
                    currentOffset++;
                    if ( currentOffset == offset ) {
                        return new Point(column,line);
                    }
                    final char c = src.charAt(i);
                    if ( c == '\n') {
                        line++;
                        column=1;
                    } else {
                        column++;
                    }
                }
                return null;
            }

            private StyledDocument document() {
                return source.getStyledDocument();
            }

            private void loadSource(File file)
            {
                Configuration.of(config).setLastSource(file);
                configProvider.save();

                final String src;
                try
                {
                    System.out.println("Loading source from "+file.getAbsolutePath());
                    src = new String( Files.readAllBytes(file.toPath() ) );
                    source.setText(src);
                }
                catch (IOException e)
                {
                    System.err.println("Failed to load source from "+file.getAbsolutePath());
                    e.printStackTrace();
                }
            }

            private void messagesChanged() {
                ((DefaultTableModel) table.getModel()).fireTableDataChanged();
            }

            private void compile()
            {
                messages.clear();
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (final ExecutableWriter writer = new ExecutableWriter(out))
                {
                    final Assembler.CompilationContext ctx = new Assembler().assemble( source.getText(), 0x200, writer );
                    ctx.messages.stream().forEach(messages::add);
                    if ( ! ctx.hasErrors() )
                    {
                        driver.runOnThread(cb -> {
                            final Consumer<Emulator> hook = ip ->
                            {
                                final byte[] data = out.toByteArray();
                                System.out.println("Loading compiled code ("+data.length+" bytes)");
                                ip.memory.write(0x200, data);
                            };
                            cb.emulator.setResetHook(hook);
                        });
                        driver.reset();
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally {
                    messagesChanged();
                }
            }
        };
    }

    private static File selectFileOpen(File file)
    {
        final JFileChooser chooser = new JFileChooser(file);
        if ( file != null ) {
            chooser.setSelectedFile( file );
        }
        int outcome = chooser.showOpenDialog(null );
        final File selected = chooser.getSelectedFile();
        if ( outcome == JFileChooser.APPROVE_OPTION && selected != null && selected.exists() && selected.isFile() )
        {
            return selected;
        }
        return null;
    }

    private static File selectFileSave(File file)
    {
        final JFileChooser chooser = new JFileChooser(file);
        if ( file != null ) {
            chooser.setSelectedFile( file );
        }
        int outcome = chooser.showSaveDialog(null );
        final File selected = chooser.getSelectedFile();
        if ( outcome == JFileChooser.APPROVE_OPTION && selected != null && ! selected.isDirectory() )
        {
            return selected;
        }
        return null;
    }

    private JMenuBar createMenuBar()
    {
        final JMenuBar bar = new JMenuBar();

        // 'File' menu
        final JMenu file = new JMenu("File");
        menuItem(file,"Quit", () -> quit() );
        bar.add( file);

        // 'View' menu
        final JMenu view = new JMenu( "View" );

        Stream.of( ConfigKey.values() ).filter( c -> c.isInternalFrame )
                .sorted( Comparator.comparing( a -> a.displayName ) )
                .forEach( key -> cbMenuItem(view, key.displayName, () ->
                                getWindow( key ).isPresent(),
                        () -> toggleVisibility( key ) )
                );
        bar.add( view );
        return bar;
    }

    private void toggleVisibility(ConfigKey configKey)
    {
        // hide window
        final MyFrame frame = windows.get( configKey );
        if ( frame != null )
        {
                frame.setVisible(false);
                Configuration.saveWindowState( config, configKey,frame);
                frame.dispose();
                windows.remove( configKey );
                desktop.remove( frame );
                configProvider.save();
                return;
        }
        // show window
        final MyFrame newFrame = createInternalFrame( configKey );
        windows.put( configKey, newFrame );
        desktop.add( newFrame );
        Configuration.of(config,configKey).setEnabled( true );
        Configuration.applyWindowState( config, configKey, newFrame );
        configProvider.save();
        driver.runOnThread( newFrame );
    }

    private Optional<MyFrame> getWindow(ConfigKey key) {
        return Optional.ofNullable( windows.get( key ) );
    }

    private void menuItem(JMenu menu, String name,Runnable r)
    {
        final JMenuItem result = new JMenuItem(name);
        result.addActionListener( ev -> r.run() );
        menu.add( result );
    }

    private void cbMenuItem(JMenu menu, String name, Supplier<Boolean> currentState,Runnable r)
    {
        final JCheckBoxMenuItem result = new JCheckBoxMenuItem( name )
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                if ( isSelected() != currentState.get() ) {
                    setSelected( currentState.get() );
                }
                super.paintComponent( g );
            }
        };
        result.addActionListener( ev -> r.run() );
        menu.add( result );
    }

    private void quit()
    {
        if ( swingTimer != null )
        {
            swingTimer.stop();
        }
        driver.destroy();
        windows.values().forEach(win -> Configuration.saveWindowState(config, win.configKey,win) );
        Configuration.saveWindowState(config, ConfigKey.MAINFRAME, MainFrame.this);
        configProvider.save();
        dispose();
        System.exit(0);
    }

    private Integer evaluate(String expression)
    {
        final Assembler.CompilationContext ctx = new Assembler.CompilationContext( new ExecutableWriter() );
        final Parser p = new Parser( new Lexer( new Scanner(expression) ) , ctx );
        ASTNode ast = p.parseExpression();

        // Lexer will lex all reserved words (which also include register names)
        // as TokenType#TEXT instead of TokenType#IDENTIFIER
        // Rewrite those AST nodes to be identifiers so that ExpressionEvaluator#evaluate()
        // will invoke our ISymbolResolver to resolve them

        final Function<ASTNode,ASTNode> mapper = node ->
        {
            if ( node instanceof TextNode )
            {
                final TextNode tn = (TextNode) node;
                if ( Identifier.ID.matcher( tn.value ).matches() )
                {
                    return new IdentifierNode( Identifier.unsafe(tn.value), tn.getRegion() );
                }
            }
            return node;
        };
        if ( ast instanceof TextNode) {
            ast = mapper.apply( ast );
        }
        else
        {
            ast.visitInOrder( (node, depth) ->
            {
                ASTNode mapped = mapper.apply( node );
                if ( mapped != node )
                {
                    node.replaceWith( mapped );
                }
            } );
        }

        System.out.println("Parsed >"+expression+"<");
        System.out.println( ast.toPrettyString() );

        if ( ctx.hasErrors() )
        {
            System.err.println("Failed to evaluate expression >"+expression+"<");
            ctx.messages.stream().forEach( System.err::println );
            return null;
        }

        final ISymbolResolver resolver = new ISymbolResolver()
        {
            @Override public SymbolTable.Symbol get(Identifier scope, Identifier name) { return get(name); }

            @Override
            public SymbolTable.Symbol get(Identifier name)
            {
                Integer result = null;
                final String v = name.value.toLowerCase();
                if ( "pc".equals( v ) ) {
                    result = driver.runOnThreadWithResult( x -> x.emulator.pc );
                }
                else if ( v.startsWith("v" ) ) {
                    try {
                        int regNum = Integer.parseInt( v.substring( 1 ) );
                        result = driver.runOnThreadWithResult( x -> x.emulator.register[regNum] );
                    }
                    catch(Exception e) {
                        // failure will be reported because of NULL symbol return
                    }
                }
                if ( result == null ) {
                    return null;
                }
                return new SymbolTable.Symbol( SymbolTable.GLOBAL_SCOPE,name, SymbolTable.Symbol.Type.LABEL,result);
            }
        };
        Object obj = null;
        try
        {
            obj = ExpressionEvaluator.evaluate( ast, resolver, true );
        }
        catch (Exception e)
        {
            System.err.println( "Failed to evalue expression >" + expression + "<" );
        }
        return obj instanceof Number ? ((Number) obj).intValue() : null;
    }
}
