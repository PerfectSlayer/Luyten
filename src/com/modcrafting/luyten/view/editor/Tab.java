package com.modcrafting.luyten.view.editor;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.modcrafting.luyten.Model;

/**
 * This class represents the tab component of editor JTabbedPane.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class Tab extends JPanel {
	/** Serialization id. */
	private static final long serialVersionUID = -514663009333644974L;
	/** Cached resource icon. */
	private static final ImageIcon CLOSE_BUTTON = new ImageIcon(Toolkit.getDefaultToolkit().getImage(Tab.class.getResource("/resources/icon_close.png")));

	/**
	 * Constructor.
	 * 
	 * @param model
	 *            The model.
	 * @param title
	 *            The tab title.
	 */
	public Tab(final Model model, String title) {
		// Create panel
		super(new GridBagLayout());
		this.setOpaque(false);
		// Create and add title
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.weightx = 1;
		this.add(new JLabel(title), constraints);
		// Create and add close button
		JLabel closeButton = new JLabel(Tab.CLOSE_BUTTON);
		constraints.gridx++;
		constraints.insets = new Insets(0, 5, 0, 0);
		constraints.anchor = GridBagConstraints.EAST;
		this.add(closeButton, constraints);
		// Attach close button behavior
		closeButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				// Check event
				if (event.getButton()!=MouseEvent.BUTTON1)
					return;
				// Close the tab
				model.closeTab(Tab.this);
			}
		});
	}
}