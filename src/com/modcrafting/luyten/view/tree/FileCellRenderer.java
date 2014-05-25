package com.modcrafting.luyten.view.tree;

import java.awt.Component;
import java.awt.Toolkit;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.modcrafting.luyten.TreeNodeUserObject;

/**
 * This class is a cell renderer to render file node.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class FileCellRenderer extends DefaultTreeCellRenderer {
	/** Serialization id. */
	private static final long serialVersionUID = -5691181006363313993L;
	/** The cache package icon. */
	private static final Icon PACKAGE_ICON = new ImageIcon(Toolkit.getDefaultToolkit().getImage(
			FileCellRenderer.class.getResource("/resources/package_obj.png")));
	/** The cached java file icon. */
	private static final Icon JAVA_FILE_ICON = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FileCellRenderer.class.getResource("/resources/java.png")));
	/** The cached default file icon. */
	private static final Icon DEFAULT_FILE_ICON = new ImageIcon(Toolkit.getDefaultToolkit().getImage(FileCellRenderer.class.getResource("/resources/file.png")));

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
		// Delegate component creation
		super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
		// Update component icon
		boolean iconSet = false;
		if (value instanceof DefaultMutableTreeNode) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
			// Check package node
			if (node.getChildCount()>0) {
				// Set package icon
				this.setIcon(FileCellRenderer.PACKAGE_ICON);
				iconSet = true;
			}
			if (!iconSet) {
				// Check known file types
				Object userObject = node.getUserObject();
				if (userObject instanceof TreeNodeUserObject) {
					String fileName = ((TreeNodeUserObject) userObject).getOriginalName();
					if (fileName.endsWith(".class")||fileName.endsWith(".java")) {
						this.setIcon(FileCellRenderer.JAVA_FILE_ICON);
						iconSet = true;
					}
				}
			}
		}
		// Set default icon
		if (!iconSet)
			this.setIcon(FileCellRenderer.DEFAULT_FILE_ICON);
		// Return component as it-self
		return this;
	}
}