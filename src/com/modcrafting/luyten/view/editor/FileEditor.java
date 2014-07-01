package com.modcrafting.luyten.view.editor;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

import org.fife.ui.rsyntaxtextarea.LinkGenerator;
import org.fife.ui.rsyntaxtextarea.LinkGeneratorResult;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.modcrafting.luyten.DecompilerLinkProvider;
import com.modcrafting.luyten.LinkProvider;
import com.modcrafting.luyten.MainWindow;
import com.modcrafting.luyten.Selection;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.Languages;

public class FileEditor {

	public static final HashSet<String> WELL_KNOWN_TEXT_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(".java", ".xml", ".rss", ".project", ".classpath", ".h",
			".sql", ".js", ".php", ".php5", ".phtml", ".html", ".htm", ".xhtm", ".xhtml", ".lua", ".bat", ".pl", ".sh", ".css", ".json", ".txt", ".rb",
			".make", ".mak", ".py", ".properties", ".prop"));

	// navigation links
	private TreeMap<Selection, String> selectionToUniqueStrTreeMap = new TreeMap<>();
	private Map<String, Boolean> isNavigableCache = new ConcurrentHashMap<>();
	private Map<String, String> readableLinksCache = new ConcurrentHashMap<>();

	private volatile boolean isContentValid = false;
	private volatile boolean isNavigationLinksValid = false;
	private volatile boolean isWaitForLinksCursor = false;

	private LinkProvider linkProvider;
	private String initialNavigationLink;
	private boolean isFirstTimeRun = true;

	MainWindow mainWindow;
	RSyntaxTextArea textArea;
	/** The file editor scrollpane. */
	private RTextScrollPane scrollPane;
	/** The file editor tab. */
	private Tab tab;

	/**
	 * Get the file editor component.
	 * 
	 * @return The file editor component.
	 */
	public JComponent getComponent() {
		return this.scrollPane;
	}

	/**
	 * Get the file editor tab.
	 * 
	 * @return The file editor tab.
	 */
	public Tab getTab() {
		return this.tab;
	}

	/**
	 * Set the file editor tab.
	 * 
	 * @param tab
	 *            The file editor tab to set.
	 */
	public void setTab(Tab tab) {
		this.tab = tab;
	}

	/** The edited resource name. */
	private String resourceName; // TODO get from path ?
	/** The edited resource path. */
	private String resourcePath; // TODO path ?

	/**
	 * Get the edited resource name.
	 * 
	 * @return The edited resource name.
	 */
	public String getResourceName() {
		return this.resourceName;
	}

	/**
	 * Get the edited resource path.
	 * 
	 * @return The edited resource path.
	 */
	public String getResourcePath() {
		return this.resourcePath;
	}

	// decompiler and type references (not needed for text files)
	private MetadataSystem metadataSystem;
	private DecompilerSettings settings;
	private DecompilationOptions decompilationOptions;
	private TypeDefinition type;

	public FileEditor(String resourceName, String resourcePath, Theme theme, MainWindow mainWindow) {
		this.resourceName = resourceName;
		this.resourcePath = resourcePath;
		this.mainWindow = mainWindow;
		// Create text area
		this.textArea = new RSyntaxTextArea(25, 70);
		this.textArea.setCaretPosition(0);
		this.textArea.requestFocusInWindow();
		this.textArea.setMarkOccurrences(true);
		this.textArea.setClearWhitespaceLinesEnabled(false);
		this.textArea.setEditable(false);
		this.textArea.setAntiAliasingEnabled(true);
		this.textArea.setCodeFoldingEnabled(true);
		this.textArea.setSyntaxEditingStyle(FileEditorSyntax.getSyntax(this.resourceName));
		scrollPane = new RTextScrollPane(this.textArea, true);
		scrollPane.setIconRowHeaderEnabled(true);
		textArea.setText("");
		theme.apply(textArea);

		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		textArea.setHyperlinksEnabled(true);
		textArea.setLinkScanningMask(InputEvent.CTRL_DOWN_MASK);

		textArea.setLinkGenerator(new LinkGenerator() {
			@Override
			public LinkGeneratorResult isLinkAtOffset(RSyntaxTextArea textArea, final int offs) {
				final String uniqueStr = getUniqueStrForOffset(offs);
				final Integer selectionFrom = getSelectionFromForOffset(offs);
				if (uniqueStr!=null&&selectionFrom!=null) {
					return new LinkGeneratorResult() {
						@Override
						public HyperlinkEvent execute() {
							if (isNavigationLinksValid)
								onNavigationClicked(uniqueStr);
							return null;
						}

						@Override
						public int getSourceOffset() {
							if (isNavigationLinksValid)
								return selectionFrom;
							return offs;
						}
					};
				}
				return null;
			}
		});

		textArea.addMouseMotionListener(new MouseMotionAdapter() {
			private boolean isLinkLabelPrev = false;
			private String prevLinkText = null;

			@Override
			public synchronized void mouseMoved(MouseEvent e) {
				String linkText = null;
				boolean isLinkLabel = false;
				boolean isCtrlDown = (e.getModifiersEx()&InputEvent.CTRL_DOWN_MASK)!=0;
				if (isCtrlDown) {
					linkText = createLinkLabel(e);
					isLinkLabel = linkText!=null;
				}
				if (isCtrlDown&&isWaitForLinksCursor) {
					textArea.setCursor(new Cursor(Cursor.WAIT_CURSOR));
				} else if (textArea.getCursor().getType()==Cursor.WAIT_CURSOR) {
					textArea.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				}

				JLabel label = FileEditor.this.mainWindow.getLabel();

				if (isLinkLabel&&isLinkLabelPrev) {
					if (!linkText.equals(prevLinkText)) {
						setLinkLabel(label, linkText);
					}
				} else if (isLinkLabel&&!isLinkLabelPrev) {
					setLinkLabel(label, linkText);

				} else if (!isLinkLabel&&isLinkLabelPrev) {
					setLinkLabel(label, null);
				}
				isLinkLabelPrev = isLinkLabel;
				prevLinkText = linkText;
			}

			private void setLinkLabel(JLabel label, String text) {
				String current = label.getText();
				if (text==null&&current!=null)
					if (current.startsWith("Navigating:")||current.startsWith("Cannot navigate:"))
						return;
				label.setText(text!=null ? text : "Complete");
			}

			private String createLinkLabel(MouseEvent e) {
				int offs = textArea.viewToModel(e.getPoint());
				if (isNavigationLinksValid) {
					return getLinkDescriptionForOffset(offs);
				}
				return null;
			}
		});
	}

	public void setContent(final String content) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				FileEditor.this.textArea.setText(content);
			}
		});
	}

	public RSyntaxTextArea getTextArea() {
		return this.textArea;
	}

	public void decompile() {
		this.invalidateContent();
		// synchronized: do not accept changes from menu while running
		boolean hasNavigationLinks = false;
		synchronized (settings) {
			hasNavigationLinks = Languages.java().equals(settings.getLanguage());
		}
		if (hasNavigationLinks) {
			decompileWithNavigationLinks();
		} else {
			decompileWithoutLinks();
		}
	}

	private void decompileWithoutLinks() {
		this.invalidateContent();
		isNavigationLinksValid = false;
		textArea.setHyperlinksEnabled(false);

		StringWriter stringwriter = new StringWriter();
		settings.getLanguage().decompileType(type, new PlainTextOutput(stringwriter), decompilationOptions);
		this.textArea.setText(stringwriter.toString());
		this.isContentValid = true;
	}

	private void decompileWithNavigationLinks() {
		this.invalidateContent();
		DecompilerLinkProvider newLinkProvider = new DecompilerLinkProvider();
		newLinkProvider.setDecompilerReferences(metadataSystem, settings, decompilationOptions);
		newLinkProvider.setType(type);
		linkProvider = newLinkProvider;

		linkProvider.generateContent();
		this.textArea.setText(linkProvider.getTextContent());
		this.isContentValid = true;
		enableLinks();
	}

	private void restoreScrollPosition(final double position) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				JScrollBar verticalScrollbar = scrollPane.getVerticalScrollBar();
				if (verticalScrollbar==null)
					return;
				int scrollMax = verticalScrollbar.getMaximum()-verticalScrollbar.getMinimum();
				long newScrollValue = Math.round(position*scrollMax)+verticalScrollbar.getMinimum();
				if (newScrollValue<verticalScrollbar.getMinimum())
					newScrollValue = verticalScrollbar.getMinimum();
				if (newScrollValue>verticalScrollbar.getMaximum())
					newScrollValue = verticalScrollbar.getMaximum();
				verticalScrollbar.setValue((int) newScrollValue);
			}
		});
	}

	private void enableLinks() {
		if (initialNavigationLink!=null) {
			doEnableLinks();
		} else {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						isWaitForLinksCursor = true;
						doEnableLinks();
					} finally {
						isWaitForLinksCursor = false;
						resetCursor();
					}
				}
			}).start();
		}
	}

	private void resetCursor() {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				textArea.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});
	}

	private void doEnableLinks() {
		isNavigationLinksValid = false;
		linkProvider.processLinks();
		buildSelectionToUniqueStrTreeMap();
		clearLinksCache();
		isNavigationLinksValid = true;
		textArea.setHyperlinksEnabled(true);
		warmUpWithFirstLink();
	}

	private void warmUpWithFirstLink() {
		if (selectionToUniqueStrTreeMap.keySet().size()>0) {
			Selection selection = selectionToUniqueStrTreeMap.keySet().iterator().next();
			getLinkDescriptionForOffset(selection.from);
		}
	}

	private void clearLinksCache() {
		try {
			isNavigableCache.clear();
			readableLinksCache.clear();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void buildSelectionToUniqueStrTreeMap() {
		TreeMap<Selection, String> treeMap = new TreeMap<>();
		Map<String, Selection> definitionToSelectionMap = linkProvider.getDefinitionToSelectionMap();
		Map<String, Set<Selection>> referenceToSelectionsMap = linkProvider.getReferenceToSelectionsMap();

		for (String key : definitionToSelectionMap.keySet()) {
			Selection selection = definitionToSelectionMap.get(key);
			treeMap.put(selection, key);
		}
		for (String key : referenceToSelectionsMap.keySet()) {
			for (Selection selection : referenceToSelectionsMap.get(key)) {
				treeMap.put(selection, key);
			}
		}
		selectionToUniqueStrTreeMap = treeMap;
	}

	private Selection getSelectionForOffset(int offset) {
		if (isNavigationLinksValid) {
			Selection offsetSelection = new Selection(offset, offset);
			Selection floorSelection = selectionToUniqueStrTreeMap.floorKey(offsetSelection);
			if (floorSelection!=null&&floorSelection.from<=offset&&floorSelection.to>offset) {
				return floorSelection;
			}
		}
		return null;
	}

	private String getUniqueStrForOffset(int offset) {
		Selection selection = getSelectionForOffset(offset);
		if (selection!=null) {
			String uniqueStr = selectionToUniqueStrTreeMap.get(selection);
			if (this.isLinkNavigable(uniqueStr)&&this.getLinkDescription(uniqueStr)!=null) {
				return uniqueStr;
			}
		}
		return null;
	}

	private Integer getSelectionFromForOffset(int offset) {
		Selection selection = getSelectionForOffset(offset);
		if (selection!=null) {
			return selection.from;
		}
		return null;
	}

	private String getLinkDescriptionForOffset(int offset) {
		String uniqueStr = getUniqueStrForOffset(offset);
		if (uniqueStr!=null) {
			String description = this.getLinkDescription(uniqueStr);
			if (description!=null) {
				return description;
			}
		}
		return null;
	}

	private boolean isLinkNavigable(String uniqueStr) {
		try {
			Boolean isNavigableCached = isNavigableCache.get(uniqueStr);
			if (isNavigableCached!=null)
				return isNavigableCached;

			boolean isNavigable = linkProvider.isLinkNavigable(uniqueStr);
			isNavigableCache.put(uniqueStr, isNavigable);
			return isNavigable;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	private String getLinkDescription(String uniqueStr) {
		try {
			String descriptionCached = readableLinksCache.get(uniqueStr);
			if (descriptionCached!=null)
				return descriptionCached;

			String description = linkProvider.getLinkDescription(uniqueStr);
			if (description!=null&&description.trim().length()>0) {
				readableLinksCache.put(uniqueStr, description);
				return description;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private void onNavigationClicked(String clickedReferenceUniqueStr) {
		if (isLocallyNavigable(clickedReferenceUniqueStr)) {
			onLocalNavigationRequest(clickedReferenceUniqueStr);
		} else if (linkProvider.isLinkNavigable(clickedReferenceUniqueStr)) {
			onOutboundNavigationRequest(clickedReferenceUniqueStr);
		} else {
			JLabel label = this.mainWindow.getLabel();
			if (label==null)
				return;
			String[] linkParts = clickedReferenceUniqueStr.split("\\|");
			if (linkParts.length<=1) {
				label.setText("Cannot navigate: "+clickedReferenceUniqueStr);
				return;
			}
			String destinationTypeStr = linkParts[1];
			label.setText("Cannot navigate: "+destinationTypeStr.replaceAll("/", "."));
		}
	}

	private boolean isLocallyNavigable(String uniqueStr) {
		return linkProvider.getDefinitionToSelectionMap().keySet().contains(uniqueStr);
	}

	private void onLocalNavigationRequest(String uniqueStr) {
		try {
			Selection selection = linkProvider.getDefinitionToSelectionMap().get(uniqueStr);
			doLocalNavigation(selection);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void doLocalNavigation(Selection selection) {
		try {
			textArea.requestFocusInWindow();
			if (selection!=null) {
				textArea.setSelectionStart(selection.from);
				textArea.setSelectionEnd(selection.to);
				scrollToSelection(selection.from);
			} else {
				textArea.setSelectionStart(0);
				textArea.setSelectionEnd(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void scrollToSelection(final int selectionBeginningOffset) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					int fullHeight = textArea.getBounds().height;
					int viewportHeight = textArea.getVisibleRect().height;
					int viewportLineCount = viewportHeight/textArea.getLineHeight();
					int selectionLineNum = textArea.getLineOfOffset(selectionBeginningOffset);
					int upperMarginToScroll = Math.round(viewportLineCount*0.29f);
					int upperLineToSet = selectionLineNum-upperMarginToScroll;
					int currentUpperLine = textArea.getVisibleRect().y/textArea.getLineHeight();

					if (selectionLineNum<=currentUpperLine+2||selectionLineNum>=currentUpperLine+viewportLineCount-4) {
						Rectangle rectToScroll = new Rectangle();
						rectToScroll.x = 0;
						rectToScroll.width = 1;
						rectToScroll.y = Math.max(upperLineToSet*textArea.getLineHeight(), 0);
						rectToScroll.height = Math.min(viewportHeight, fullHeight-rectToScroll.y);
						textArea.scrollRectToVisible(rectToScroll);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void onOutboundNavigationRequest(String uniqueStr) {
		mainWindow.onNavigationRequest(uniqueStr);
	}

	public void setDecompilerReferences(MetadataSystem metadataSystem, DecompilerSettings settings, DecompilationOptions decompilationOptions) {
		this.metadataSystem = metadataSystem;
		this.settings = settings;
		this.decompilationOptions = decompilationOptions;
	}

	public TypeDefinition getType() {
		return type;
	}

	public void setType(TypeDefinition type) {
		this.type = type;
	}

	public boolean isContentValid() {
		return isContentValid;
	}

	public void invalidateContent() {
		try {
			this.setContent("");
		} finally {
			this.isContentValid = false;
			this.isNavigationLinksValid = false;
		}
	}

	public void setInitialNavigationLink(String initialNavigationLink) {
		this.initialNavigationLink = initialNavigationLink;
	}

	public void onAddedToScreen() {
		try {
			if (initialNavigationLink!=null) {
				onLocalNavigationRequest(initialNavigationLink);
			} else if (isFirstTimeRun) {
				// warm up scrolling
				isFirstTimeRun = false;
				doLocalNavigation(new Selection(0, 0));
			}
		} finally {
			initialNavigationLink = null;
		}
	}

	/**
	 * sun.swing.CachedPainter holds on OpenFile for a while even after JTabbedPane.remove(component)
	 */
	public void close() {
		linkProvider = null;
		type = null;
		invalidateContent();
		clearLinksCache();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime*result+(resourcePath==null ? 0 : resourcePath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this==obj)
			return true;
		if (obj==null)
			return false;
		if (getClass()!=obj.getClass())
			return false;
		FileEditor other = (FileEditor) obj;
		if (this.resourcePath==null) {
			if (other.resourcePath!=null)
				return false;
		} else if (!this.resourcePath.equals(other.resourcePath))
			return false;
		return true;
	}
}
