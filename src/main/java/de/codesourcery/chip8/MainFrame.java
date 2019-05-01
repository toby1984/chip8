package de.codesourcery.chip8;

import de.codesourcery.chip8.asm.Assembler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MainFrame extends JFrame
{
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
            System.out.println("Window: "+win.getTitle());
            ConfigSerializer.applyWindowState(config, win.windowKey,win);
            win.invoke( driver );
            desktop.add( win );
        });
        setContentPane( desktop );
        setSize( new Dimension(640,400) );
        setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        ConfigSerializer.applyWindowState(config, "mainframe", MainFrame.this);
        setVisible( true );
        setLocationRelativeTo( null );

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                if ( swingTimer != null )
                {
                    swingTimer.stop();
                }
                driver.terminate();
                windows.forEach(win -> ConfigSerializer.saveWindowState(config, win.windowKey,win) );
                ConfigSerializer.saveWindowState(config, "mainframe", MainFrame.this);
                configProvider.save();
                dispose();
            }
        });
    }

    protected abstract class MyFrame extends JInternalFrame implements InterpreterDriver.IDriverCallback, InterpreterDriver.IStateListener
    {
        public final String windowKey;

        public MyFrame(String title,String windowKey,boolean needsTick,boolean needsState)
        {
            super(title,true,true,true);
            if ( needsTick ) {
                driver.registerTickListener( this );
            }
            if ( needsState ) {
                driver.registerStateListener( this );
            }
            this.setDefaultCloseOperation( JInternalFrame.DISPOSE_ON_CLOSE );
            this.windowKey = windowKey;
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
        return new MyFrame("Memory","memory", true,false )
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
        return new MyFrame("Screen","screen", false,false ) {

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
        return new MyFrame("Buttons", "buttons", false,true) {

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
                    final ConfigSerializer serializer = new ConfigSerializer(config);
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
        return new MyFrame("CPU","cpu", true,false) {

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
        return new MyFrame("Disasm", "disasm", true,false) {

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

        return new MyFrame("Assembler", "asm", false,false) {

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

                    final File last = new ConfigSerializer(config).getLastSource();
                    final File file = selectFileSave(last);
                    if ( file != null )
                    {
                        try (FileWriter w = new FileWriter(file) )
                        {
                            final String text = source.getText();
                            w.write(text==null ? "": text );
                            System.out.println("Saved source to "+file.getAbsolutePath());
                            new ConfigSerializer(config).setLastSource(file);
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
                    final File last = new ConfigSerializer(config).getLastSource();
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

                final File last = new ConfigSerializer(config).getLastSource();
                if ( last != null ) {
                    loadSource(last);
                }
            }

            private void loadSource(File file)
            {
                new ConfigSerializer(config).setLastSource(file);
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
}