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
import de.codesourcery.chip8.emulator.Interpreter;
import de.codesourcery.chip8.emulator.InterpreterDriver;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
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
        public void stateChanged(InterpreterDriver controller, boolean newState) { }
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
                final Panel p = new Panel( driver.interpreter.screen,
                    driver.interpreter.keyboard );
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

            {
                getContentPane().setLayout( new FlowLayout() );
                getContentPane().add( startButton );
                getContentPane().add( stepButton );
                getContentPane().add( stopButton );
                getContentPane().add( resetButton );
                getContentPane().add( loadButton );

                stopButton.setEnabled( false );
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
            public void stateChanged(InterpreterDriver controller, boolean isRunning)
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

            private final JTextArea source = new JTextArea();
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

            {
                configure(source);
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
