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
package de.codesourcery.chip8.asm.parser;

import de.codesourcery.chip8.asm.Assembler;
import de.codesourcery.chip8.asm.ExecutableWriter;
import de.codesourcery.chip8.asm.ast.ASTNode;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Crude Swing UI to interactively inspect the AST generated by the parser.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ParserTest extends JFrame
{
    private JTextArea src = new JTextArea();
    private JTextArea messages = new JTextArea();
    private JTree astTree = new JTree();
    private JButton compile= new JButton("Compile");

    public static void main(String[] args) throws InvocationTargetException, InterruptedException
    {
        SwingUtilities.invokeAndWait( () -> new ParserTest() );
    }

    public ParserTest()
    {
        setPreferredSize( new Dimension(640,480) );
        setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        compile.addActionListener( ev-> compile() );

        setLayout( new GridBagLayout() );

        src.setRows( 5 );
        src.setColumns( 40 );

        messages.setRows( 5 );
        messages.setColumns( 40 );

        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.gridx = 0; cnstrs.gridy= 0;
        cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.0;
        cnstrs.fill = GridBagConstraints.BOTH;
        add( src, cnstrs );

        cnstrs = new GridBagConstraints();
        cnstrs.gridx = 1; cnstrs.gridy= 0;
        cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 0.0; cnstrs.weighty = 0.0;
        cnstrs.fill = GridBagConstraints.NONE;
        add( compile, cnstrs );

        cnstrs = new GridBagConstraints();
        cnstrs.gridx = 0; cnstrs.gridy= 1;
        cnstrs.gridwidth = 2 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.8;
        cnstrs.fill = GridBagConstraints.BOTH;
        add( new JScrollPane(astTree), cnstrs );

        cnstrs = new GridBagConstraints();
        cnstrs.gridx = 0; cnstrs.gridy= 2;
        cnstrs.gridwidth = 2 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 1.0; cnstrs.weighty = 0.2;
        cnstrs.fill = GridBagConstraints.BOTH;
        messages.setEditable( false );
        add( messages , cnstrs );
        pack();
        setVisible( true );
        setLocationRelativeTo( null );
    }

    private void compile()
    {
        try
        {
            final Lexer lexer = new Lexer( new Scanner( src.getText() ) );
            final Parser p = new Parser( lexer , new Assembler.CompilationContext( new ExecutableWriter() ) );
            final ASTNode ast = p.parse();
            astTree.setModel( new TreeModel()
            {
                private final List<TreeModelListener> listeners = new ArrayList<>();

                @Override
                public Object getRoot()
                {
                    return ast;
                }

                @Override
                public Object getChild(Object parent, int index)
                {
                    return ((ASTNode) parent).child( index );
                }

                @Override
                public int getChildCount(Object parent)
                {
                    return ((ASTNode) parent).childCount();
                }

                @Override
                public boolean isLeaf(Object node)
                {
                    return ((ASTNode) node).childCount() == 0;
                }

                @Override
                public void valueForPathChanged(TreePath path, Object newValue)
                {
                }

                @Override
                public int getIndexOfChild(Object parent, Object child)
                {
                    return ((ASTNode) parent).children.indexOf( child );
                }

                @Override
                public void addTreeModelListener(TreeModelListener l)
                {
                    listeners.add( l );
                }

                @Override
                public void removeTreeModelListener(TreeModelListener l)
                {
                    listeners.remove( l );
                }
            } );
            messages.setText("Compiled successfully on "+ ZonedDateTime.now() );
        }
        catch(Exception e)
        {
            final ByteArrayOutputStream exMessage = new ByteArrayOutputStream();
            final PrintWriter writer = new PrintWriter( exMessage );
            e.printStackTrace( writer );
            writer.close();
            messages.setText("Compilation FAILED on "+ ZonedDateTime.now()+"\n\n"+new String(exMessage.toByteArray()) );
        }
    }
}
