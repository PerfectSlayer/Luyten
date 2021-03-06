package com.modcrafting.luyten;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import com.modcrafting.luyten.view.find.FindBox;

/**
 * Dispatcher
 */
public class MainWindow extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final String TITLE = "Luyten";

	private Model model;
	private JProgressBar bar;
	private JLabel label;
	private FindBox findBox;
	private ConfigSaver configSaver;
	private WindowPosition windowPosition;
	private LuytenPreferences luytenPrefs;
	private FileDialog fileDialog;
	private FileSaver fileSaver;

	public MainWindow(File fileFromCommandLine) {
		configSaver = ConfigSaver.getLoadedInstance();
		windowPosition = configSaver.getMainWindowPosition();
		luytenPrefs = configSaver.getLuytenPreferences();

		MainMenuBar mainMenuBar = new MainMenuBar(this);
		this.setJMenuBar(mainMenuBar);

		this.adjustWindowPositionBySavedState();
		this.setHideFindBoxOnMainWindowFocus();
		this.setQuitOnWindowClosing();
		this.setTitle(MainWindow.TITLE);

		JPanel footerPanel = new JPanel();
		footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.X_AXIS));
		footerPanel.setPreferredSize(new Dimension(this.getWidth(), 24));

		label = new JLabel();
		label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
		label.setPreferredSize(new Dimension(this.getWidth() / 2, 20));
		footerPanel.add(label);

		bar = new JProgressBar();
		bar.setIndeterminate(true);
		bar.setOpaque(false);
		bar.setVisible(false);
		bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		bar.setPreferredSize(new Dimension(this.getWidth() / 2, 20));
		footerPanel.add(bar);
		this.add(footerPanel, BorderLayout.SOUTH);

		model = new Model(this);
		this.getContentPane().add(model);

		if (fileFromCommandLine != null) {
			model.loadFile(fileFromCommandLine);
		}

		try {
			DropTarget dt = new DropTarget();
			dt.addDropTargetListener(new DropListener(this));
			this.setDropTarget(dt);
		} catch (Exception e) {
			e.printStackTrace();
		}

		fileDialog = new FileDialog(this);
		fileSaver = new FileSaver(bar, label);

		this.setExitOnEscWhenEnabled(model);
	}

	public void onOpenFileMenu() {
		File selectedFile = fileDialog.doOpenDialog();
		if (selectedFile != null) {
			this.getModel().loadFile(selectedFile);
		}
	}

	public void onCloseFileMenu() {
		this.getModel().closeFile();
	}

	public void onSaveAsMenu() {
		// Check if at least one text area is opened
		RSyntaxTextArea textArea = this.getModel().getCurrentTextArea();
		if (textArea == null)
			return;
		// Get tab title
		String tabTitle = this.getModel().getCurrentTabTitle();
		if (tabTitle == null)
			return;
		// Compute recommended file name
		String recommendedFileName = tabTitle;
		if (recommendedFileName.endsWith(".class"))
			recommendedFileName = recommendedFileName.substring(0, recommendedFileName.length()-6)+".java";
		// Display save dialog to select file
		File selectedFile = this.fileDialog.doSaveDialog(recommendedFileName);
		if (selectedFile == null)
			return;
		// Save file
		this.fileSaver.saveText(textArea.getText(), selectedFile);
	}

	public void onSaveAllMenu() {
		File openedFile = this.getModel().getOpenedFile();
		if (openedFile == null)
			return;

		String fileName = openedFile.getName();
		if (fileName.endsWith(".class")) {
			fileName = fileName.substring(0, fileName.length()-6)+".java";
		} else if (fileName.toLowerCase().endsWith(".jar")) {
			fileName = "decompiled-" + fileName.replaceAll("\\.[jJ][aA][rR]", ".zip");
		} else {
			fileName = "saved-" + fileName;
		}

		File selectedFileToSave = fileDialog.doSaveAllDialog(fileName);
		if (selectedFileToSave != null) {
			fileSaver.saveAllDecompiled(openedFile, selectedFileToSave);
		}
	}

	public void onExitMenu() {
		quit();
	}

	public void onSelectAllMenu() {
		try {
			RSyntaxTextArea pane = this.getModel().getCurrentTextArea();
			if (pane != null) {
				pane.requestFocusInWindow();
				pane.setSelectionStart(0);
				pane.setSelectionEnd(pane.getText().length());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void onFindMenu() {
		// Check if at least file editor is opened
		RSyntaxTextArea textArea = this.getModel().getCurrentTextArea();
		if (textArea==null)
			return;
		// Check if find box is instantiated
		if (this.findBox==null)
			this.findBox = new FindBox(this);
		// Show find box
		this.findBox.showFindBox();
	}

	public void onLegalMenu() {
		new Thread() {
			public void run() {
				try {
					bar.setVisible(true);
					String legalStr = getLegalStr();
					MainWindow.this.getModel().showLegal(legalStr);
				} finally {
					bar.setVisible(false);
				}
			}
		}.start();
	}

	private String getLegalStr() {
		StringBuilder sb = new StringBuilder();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(getClass()
					.getResourceAsStream("/distfiles/Procyon.License.txt")));
			String line;
			while ((line = reader.readLine()) != null)
				sb.append(line).append("\n");
			sb.append("\n\n\n\n\n");
			reader = new BufferedReader(new InputStreamReader(getClass()
					.getResourceAsStream("/distfiles/RSyntaxTextArea.License.txt")));
			while ((line = reader.readLine()) != null)
				sb.append(line).append("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sb.toString();
	}

	public void onThemesChanged() {
		this.getModel().changeTheme(luytenPrefs.getThemeXml());
	}

	public void onSettingsChanged() {
		this.getModel().updateOpenClasses();
	}

	public void onTreeSettingsChanged() {
		this.getModel().updateTree();
	}

	public void onFileDropped(File file) {
		if (file != null) {
			this.getModel().loadFile(file);
		}
	}

	public void onFileLoadEnded(File file, boolean isSuccess) {
		try {
			if (file != null && isSuccess) {
				this.setTitle(TITLE + " - " + file.getName());
			} else {
				this.setTitle(TITLE);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void onNavigationRequest(String uniqueStr) {
		this.getModel().navigateTo(uniqueStr);
	}

	private void adjustWindowPositionBySavedState() {
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		if (!windowPosition.isSavedWindowPositionValid()) {
			final Dimension center = new Dimension((int) (screenSize.width * 0.75), (int) (screenSize.height * 0.75));
			final int x = (int) (center.width * 0.2);
			final int y = (int) (center.height * 0.2);
			this.setBounds(x, y, center.width, center.height);

		} else if (windowPosition.isFullScreen()) {
			int heightMinusTray = screenSize.height;
			if (screenSize.height > 30)
				heightMinusTray -= 30;
			this.setBounds(0, 0, screenSize.width, heightMinusTray);
			this.setExtendedState(JFrame.MAXIMIZED_BOTH);

			this.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					if (MainWindow.this.getExtendedState() != JFrame.MAXIMIZED_BOTH) {
						windowPosition.setFullScreen(false);
						if (windowPosition.isSavedWindowPositionValid()) {
							MainWindow.this.setBounds(windowPosition.getWindowX(), windowPosition.getWindowY(),
									windowPosition.getWindowWidth(), windowPosition.getWindowHeight());
						}
						MainWindow.this.removeComponentListener(this);
					}
				}
			});

		} else {
			this.setBounds(windowPosition.getWindowX(), windowPosition.getWindowY(),
					windowPosition.getWindowWidth(), windowPosition.getWindowHeight());
		}
	}

	private void setHideFindBoxOnMainWindowFocus() {
		this.addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowGainedFocus(WindowEvent e) {
				if (findBox != null && findBox.isVisible()) {
					findBox.setVisible(false);
				}
			}
		});
	}

	private void setQuitOnWindowClosing() {
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});
	}

	private void quit() {
		try {
			windowPosition.readPositionFromWindow(this);
			configSaver.saveConfig();
		} catch (Exception exc) {
			exc.printStackTrace();
		} finally {
			try {
				this.dispose();
			} finally {
				System.exit(0);
			}
		}
	}

	private void setExitOnEscWhenEnabled(JComponent mainComponent) {
		Action escapeAction = new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (luytenPrefs.isExitByEscEnabled()) {
					quit();
				}
			}
		};

		KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
		mainComponent.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(escapeKeyStroke, "ESCAPE");
		mainComponent.getActionMap().put("ESCAPE", escapeAction);
	}

	public Model getModel() {
		return model;
	}

	public JProgressBar getBar() {
		return bar;
	}

	public JLabel getLabel() {
		return label;
	}
}
