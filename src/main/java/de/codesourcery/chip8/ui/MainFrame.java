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
import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.ast.TextNode;
import de.codesourcery.chip8.asm.ast.TextRegion;
import de.codesourcery.chip8.asm.parser.Lexer;
import de.codesourcery.chip8.asm.parser.Parser;
import de.codesourcery.chip8.asm.parser.Scanner;
import de.codesourcery.chip8.emulator.Breakpoint;
import de.codesourcery.chip8.emulator.Interpreter;
import de.codesourcery.chip8.emulator.InterpreterDriver;
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
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
        MEMORY("memory","Memory");

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

    private final List<MyFrame> windows;
    private final InterpreterDriver driver;

    private final IConfigurationProvider configProvider;
    private final Properties config;

    public MainFrame(InterpreterDriver driver, IConfigurationProvider configProvider)
    {
        Validate.notNull( driver, "controller must not be null" );
        Validate.notNull(configProvider, "configProvider must not be null");
        this.driver = driver;
        this.configProvider = configProvider;
        this.config = configProvider.load();
        windows = createFrames();
        final JDesktopPane desktop = new JDesktopPane();
        windows.forEach( win ->
        {
            Configuration.applyWindowState(config, win.configKey,win);
            win.invoke( driver );
            desktop.add( win );
        });
        setJMenuBar( createMenuBar() );
        setContentPane( desktop );
        setSize( new Dimension(640,400) );
        setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        Configuration.applyWindowState(config, ConfigKey.MAINFRAME, MainFrame.this);
        setVisible( true );
        setLocationRelativeTo( null );
        addWindowListener(new WindowAdapter() {@Override public void windowClosing(WindowEvent e) { quit(); } } );
    }

    protected abstract class MyFrame extends JInternalFrame implements InterpreterDriver.IDriverCallback, InterpreterDriver.IStateListener
    {
        public final ConfigKey configKey;

        public MyFrame(String title, ConfigKey configKey, boolean needsTick, boolean needsState)
        {
            super(title,true,true,true);
            if ( needsTick ) {
                driver.registerTickListener( this );
            }
            if ( needsState ) {
                driver.registerStateListener( this );
            }
            this.setDefaultCloseOperation( JInternalFrame.DISPOSE_ON_CLOSE );
            this.configKey = configKey;
            configure(this);
        }

        protected final void configure(Component component) {
            component.setFont(new Font("monospaced", Font.PLAIN, 12));
        }

        @Override
        public void dispose()
        {
            driver.removeTickListener( this );
            driver.removeStateListener( this );
            super.dispose();
        }

        protected final String hexByte(int value) {
            return "0x" + StringUtils.leftPad( Integer.toString( value, 16 ), 2, '0' );
        }

        protected final String hexWord(int value) {
            return "0x" + StringUtils.leftPad( Integer.toString( value, 16 ), 4, '0' );
        }

        @Override
        public void invoke(InterpreterDriver controller) { }

        @Override
        public void stateChanged(InterpreterDriver controller, InterpreterDriver.Reason reason) { }
    }

    private List<MyFrame> createFrames()
    {
        final List<MyFrame> result = new ArrayList<>();
        result.add( createScreenView() );
        result.add( createButtonsView() );
        result.add( createDisasmView() );
        result.add( createCPUView() );
        result.add( createAsmView() );
        result.add( createMemoryView() );
        return result;
    }

    private MyFrame createMemoryView()
    {
        return new MyFrame("Memory", ConfigKey.MEMORY, true,false )
        {
            private final JTextArea dump = new JTextArea();

            {
                dump.setEditable(false);
                configure(dump);
                getContentPane().add( dump );
            }

            @Override
            public void invoke(InterpreterDriver controller)
            {
                final int pc = controller.interpreter.pc;
                final String text = controller.interpreter.memory.dump(pc,128,16);
                SwingUtilities.invokeLater(() -> dump.setText(text) );
            }
        };
    }

    private MyFrame createScreenView()
    {
        return new MyFrame("Screen", ConfigKey.SCREEN, false,false ) {

            {
                final Panel p = new Panel( driver );
                getContentPane().add( p );
                final AtomicBoolean screenChanged = new AtomicBoolean();
                swingTimer = new Timer(16, ev ->
                {
                    driver.runOnThread(driver ->
                    {
                        final boolean updateScreen = driver.interpreter.screen.hasChanged();
                        if (updateScreen)
                        {
                            p.draw(driver.interpreter.screen);
                        }
                        screenChanged.set(updateScreen);
                    });
                    if ( screenChanged.get() )
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
            private final JButton stopButton=new JButton("Stop");
            private final JButton loadButton=new JButton("Load");
            private final JSlider speed = new JSlider(0,1000,500);

            {
                getContentPane().setLayout( new FlowLayout() );
                getContentPane().add( startButton );
                getContentPane().add( stepButton );
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
                            driver.interpreter.setResetHook( interpreter ->
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
            public void stateChanged(InterpreterDriver controller, InterpreterDriver.Reason reason)
            {
                SwingUtilities.invokeLater(  () ->
                {
                    final boolean isRunning = reason == InterpreterDriver.Reason.STARTED;
                    startButton.setEnabled( !isRunning );
                    stopButton.setEnabled( isRunning );
                    stepButton.setEnabled( !isRunning );
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
            public void invoke(InterpreterDriver controller)
            {
                final Interpreter interpreter = controller.interpreter;
                buffer.setLength(0);
                for ( int i = 0,len=16,inThisRow=0 ; i < len ; i++,inThisRow++ )
                {
                    String regNum = StringUtils.leftPad(Integer.toString(i),2,' ');
                    String reg = "Register "+regNum+": "+hexByte( interpreter.register[i] );
                    buffer.append(reg);
                    if ( ( inThisRow % 4 ) == 0 && inThisRow > 0 ) {
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
        return new MyFrame("Disasm", ConfigKey.DISASM, true,false) {

            // @GuardedBy( lines )
            private int startAddress;
            // @GuardedBy( lines )
            private int currentPC;

            // @GuardedBy( lines )
            private final List<String> lines = new ArrayList<>();

            private void toggleBreakpoint(int address) {
                driver.toggle(new Breakpoint(address,false) );
                driver.runOnThread(this );
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
                }
                @Override
                protected void paintComponent(Graphics g)
                {
                    super.paintComponent(g);
                    final int fontHeight = g.getFontMetrics().getHeight();
                    int x0 = 0;
                    int y0 = fontHeight;

                    final Map<Integer, Breakpoint> bps = new HashMap<>();
                    driver.runOnThread(ip ->
                    {
                        synchronized (bps)
                        {
                            ip.getAllBreakpoints().stream()
                                .filter(x -> !x.isTemporary)
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
            public void invoke(InterpreterDriver controller)
            {
                synchronized(this.lines)
                {
                    final Interpreter interpreter = controller.interpreter;
                    this.currentPC = interpreter.pc;

                    final int displayStart;
                    if ( Math.abs( currentPC - startAddress ) > 7 ) {
                        displayStart = Math.max(0,currentPC-8);
                    } else {
                        displayStart = startAddress;
                    }
                    final List<String> lines = Disassembler.disAsm( interpreter.memory,displayStart,16 );

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
        class Message
        {
            public final ZonedDateTime timestamp = ZonedDateTime.now();
            public final String text;

            Message(String text)
            {
                this.text = text;
            }
        }

        return new MyFrame("Assembler", ConfigKey.ASM, false,false) {

            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            private final JButton compile = new JButton("Compile");
            private final JButton load = new JButton("Load");
            private final JButton save = new JButton("Save");

            private final Thread highlightThread;
            private final Object DOC_LOCK = new Object();
            private boolean documentChanged = true;
            private boolean documentChangeListenerEnabled=true;


            private final JTextPane source = new JTextPane();
            private final List<Message> messages = new ArrayList<>();
            private final JTable table = new JTable(new DefaultTableModel()
            {
                @Override public int getRowCount() { return messages.size(); }

                @Override
                public int getColumnCount() {return 2;}

                @Override
                public String getColumnName(int columnIndex)
                {
                    switch(columnIndex) {
                        case 0: return "Time";
                        case 1: return "Message";
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
                        case 1: return messages.get(rowIndex).text;
                    }
                    throw new IllegalArgumentException("Invalid column "+columnIndex);
                }

                @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) { }
            });

            private final Style defaultStyle;
            private final Style insnStyle;
            private final Style identifierStyle;
            private final Style registerStyle;
            private final Style textStyle;

            {
                insnStyle = document().addStyle( "instruction", null );
                StyleConstants.setForeground(insnStyle, Color.BLUE);

                identifierStyle = document().addStyle( "identifier", null );
                StyleConstants.setForeground(identifierStyle, Color.GREEN);

                registerStyle = document().addStyle( "register", null );
                StyleConstants.setForeground(registerStyle, Color.MAGENTA);

                textStyle = document().addStyle( "text", null );
                StyleConstants.setForeground(textStyle, Color.BLACK);
                StyleConstants.setBold(textStyle,true);

                defaultStyle = document().addStyle( "defaultStyle", null );

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
                                        System.out.println("Highlighting text");
                                        documentChangeListenerEnabled = false;
                                        final Parser p = new Parser(new Lexer(new Scanner(text)));
                                        final ASTNode ast;
                                        try
                                        {
                                            ast = p.parse();
                                        }
                                        catch(Exception e) {
                                            return;
                                        }
                                        final StyledDocument document = document();
                                        document.setCharacterAttributes(0,text.length(),defaultStyle,true );
                                        ast.visitInOrder((node, depth) ->
                                        {
                                            final TextRegion region = node.getRegion();
                                            if (node instanceof InstructionNode)
                                            {
                                                document.setCharacterAttributes(
                                                    region.getStartingOffset(), region.getLength(), insnStyle, true);
                                            } else if ( node instanceof LabelNode ) {
                                                document.setCharacterAttributes(
                                                    region.getStartingOffset(), region.getLength(), identifierStyle, true);
                                            } else if ( node instanceof TextNode ) {
                                                document.setCharacterAttributes(
                                                    region.getStartingOffset(), region.getLength(), textStyle, true);
                                            } else if ( node instanceof RegisterNode ) {
                                                document.setCharacterAttributes(
                                                    region.getStartingOffset(), region.getLength(), registerStyle, true);
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

            private void msg(String s) {
                messages.add( new Message(s) );
            }

            private void messagesChanged() {
                ((DefaultTableModel) table.getModel()).fireTableDataChanged();
            }

            private void compile()
            {
                messages.clear();
                messagesChanged();

                final ByteArrayOutputStream out = new ByteArrayOutputStream();

                msg("Compilation started.");
                try
                {
                    final int bytesWritten = Assembler.assemble(source.getText(), out);
                    msg("Compilation succeeded ("+bytesWritten+" bytes)");
                    driver.runOnThread(cb -> {
                        final Consumer<Interpreter> hook = ip ->
                        {
                            final byte[] data = out.toByteArray();
                            System.out.println("Loading compiled code ("+data.length+" bytes)");
                            ip.memory.write(0x200, data);
                        };
                        cb.interpreter.setResetHook(hook);
                    });
                    driver.reset();
                }
                catch (Exception e)
                {
                    final StringWriter sWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter( sWriter ));
                    msg( sWriter.toString() );
                    msg("Compilation failed.");
                } finally {
                    messagesChanged();
                }
            }
        };
    }

    private static File selectFileOpen(File file)
    {
        final JFileChooser chooser = new JFileChooser(file);
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
                                                                   getWindow( key )
                                                                       .map( Component::isVisible )
                                                                       .orElse( false ),
                () -> toggleVisibility( key ) )
            );
        bar.add( view );
        return bar;
    }

    private void toggleVisibility(ConfigKey configKey)
    {
        windows.stream().filter( x -> x.configKey.equals( configKey ) )
            .forEach( w ->
            {
                w.setVisible( !w.isVisible() );
                Configuration.saveWindowState( config, configKey,w);
            });

        configProvider.save();
    }

    private Optional<MyFrame> getWindow(ConfigKey key) {
        return windows.stream().filter( x -> x.configKey == key ).findFirst();
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
        driver.terminate();
        windows.forEach(win -> Configuration.saveWindowState(config, win.configKey,win) );
        Configuration.saveWindowState(config, ConfigKey.MAINFRAME, MainFrame.this);
        configProvider.save();
        dispose();
    }
}
