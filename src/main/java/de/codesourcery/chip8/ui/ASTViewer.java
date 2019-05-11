package de.codesourcery.chip8.ui;

import de.codesourcery.chip8.asm.ast.AST;
import de.codesourcery.chip8.asm.ast.ASTNode;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.HeadlessException;

public class ASTViewer extends JFrame
{
    private ASTNode ast = new AST();

    private final JTree tree = new JTree();

    private final class MyTreeModel extends DefaultTreeModel
    {
        public MyTreeModel()
        {
            super( null );
        }

        public void fireDataChanged()
        {
            fireTreeStructureChanged( this,new Object[]{ast} ,new int[0], new Object[0]  );
        }
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
            return ((ASTNode) node).hasNoChildren();
        }

        @Override
        public void valueForPathChanged(TreePath path, Object newValue)
        {
        }

        @Override
        public int getIndexOfChild(Object parent, Object child)
        {
            return ((ASTNode) parent).children.indexOf( (ASTNode) child );
        }
    };

    private final MyTreeModel treeModel = new MyTreeModel();

    public ASTViewer() throws HeadlessException
    {
        super("AST viewer");
        tree.setModel( treeModel );
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add( new JScrollPane( tree ), BorderLayout.CENTER );
        setPreferredSize( new Dimension(320,400 ) );
        pack();
        setLocationRelativeTo( null );
        tree.setCellRenderer( new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
            {
                final Component result = super.getTreeCellRendererComponent( tree, value, sel, expanded, leaf, row, hasFocus );
                setText( ((ASTNode) value).toString() );
                return result;
            }
        } );
    }

    public void setAST(ASTNode ast) {
        this.ast = ast.createCopy();
        treeModel.fireDataChanged();
    }
}