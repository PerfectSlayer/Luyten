package com.modcrafting.luyten;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.modcrafting.luyten.model.exception.FileEntryNotFoundException;
import com.modcrafting.luyten.model.exception.FileIsBinaryException;
import com.modcrafting.luyten.model.exception.TooLargeFileException;
import com.modcrafting.luyten.model.tree.ResourceNode;
import com.modcrafting.luyten.view.editor.Tab;
import com.modcrafting.luyten.view.find.FindBox;
import com.modcrafting.luyten.view.tree.FileCellRenderer;
import com.strobel.assembler.InputTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.core.StringUtilities;
import com.strobel.core.VerifyArgument;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

/**
 * Jar-level model
 */
public class Model extends JSplitPane {
	private static final long serialVersionUID = 6896857630400910200L;

	private static final long MAX_JAR_FILE_SIZE_BYTES = 1_000_000_000;
	private static final long MAX_UNPACKED_FILE_SIZE_BYTES = 1_000_000;

	private final LuytenTypeLoader typeLoader = new LuytenTypeLoader();
	private MetadataSystem metadataSystem = new MetadataSystem(typeLoader);

	private JTree tree;
	private JTabbedPane house;
	private File file;
	private DecompilerSettings settings;
	private DecompilationOptions decompilationOptions;
	private Theme theme;
	private MainWindow mainWindow;
	private JProgressBar bar;
	private JLabel label;
	private HashSet<OpenFile> hmap = new HashSet<OpenFile>();
	private Set<String> treeExpansionState;
	private boolean open = false;
	private State state;
	private ConfigSaver configSaver;
	private LuytenPreferences luytenPrefs;

	public Model(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.bar = mainWindow.getBar();
		this.label = mainWindow.getLabel();

		configSaver = ConfigSaver.getLoadedInstance();
		settings = configSaver.getDecompilerSettings();
		luytenPrefs = configSaver.getLuytenPreferences();

		try {
			String themeXml = luytenPrefs.getThemeXml();
			theme = Theme.load(getClass().getResourceAsStream(LuytenPreferences.THEME_XML_PATH+themeXml));
		} catch (Exception e1) {
			try {
				e1.printStackTrace();
				String themeXml = LuytenPreferences.DEFAULT_THEME_XML;
				luytenPrefs.setThemeXml(themeXml);
				theme = Theme.load(getClass().getResourceAsStream(LuytenPreferences.THEME_XML_PATH+themeXml));
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}

		tree = new JTree();
		tree.setModel(new DefaultTreeModel(null));
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.setCellRenderer(new FileCellRenderer());
		TreeListener tl = new TreeListener();
		tree.addMouseListener(tl);

		JPanel panel2 = new JPanel();
		panel2.setLayout(new BoxLayout(panel2, 1));
		panel2.setBorder(BorderFactory.createTitledBorder("Structure"));
		panel2.add(new JScrollPane(tree));

		house = new JTabbedPane();
		house.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		house.addChangeListener(new TabChangeListener());

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, 1));
		panel.setBorder(BorderFactory.createTitledBorder("Code"));
		panel.add(house);
		this.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
		this.setDividerLocation(250%mainWindow.getWidth());
		this.setLeftComponent(panel2);
		this.setRightComponent(panel);

		decompilationOptions = new DecompilationOptions();
		decompilationOptions.setSettings(settings);
		decompilationOptions.setFullDecompilation(true);
	}

	public void showLegal(String legalStr) {
		OpenFile open = new OpenFile("Legal", "*/Legal", theme, mainWindow);
		open.setContent(legalStr);
		hmap.add(open);
		addOrSwitchToTab(open);
	}

	private void addOrSwitchToTab(final OpenFile open) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					// Check if file is already in editor
					Tab tab = open.tab;
					if (tab==null) {
						// Create file view and tab component
						String title = open.name;
						tab = new Tab(Model.this, title);
						open.tab = tab;
						house.addTab(title, open.scrollPane);
						int index = house.getTabCount()-1;
						house.setTabComponentAt(index, tab);
						house.setSelectedIndex(index);
					} else {
						// Get index of the tab
						int index = Model.this.getTabIndex(tab);
						if (index==-1)
							return;
						// Select the tab
						house.setSelectedIndex(index);
					}

					open.onAddedToScreen();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Close an editor tab.
	 * 
	 * @param tab
	 *            The tab to close.
	 */
	public void closeTab(Tab tab) {
		// Get index of the tab
		int index = this.getTabIndex(tab);
		if (index==-1)
			return;
		RTextScrollPane co = (RTextScrollPane) house.getComponentAt(index);
		RSyntaxTextArea pane = (RSyntaxTextArea) co.getViewport().getView();
		OpenFile open = null;
		for (OpenFile file : hmap)
			if (pane.equals(file.textArea))
				open = file;
		if (open!=null&&hmap.contains(open))
			hmap.remove(open);
		house.remove(co);
		if (open!=null)
			open.close();
	}

	/**
	 * Get the index of an editor tab.
	 * 
	 * @param tab
	 *            The tab to get index.
	 * @return The index of the tab, <code>-1</code> if the tab is not found.
	 */
	private int getTabIndex(Tab tab) {
		// Check the tab
		if (tab==null)
			return -1;
		// Get index of the tab
		int index = 0;
		int nbrIndex = this.house.getTabCount();
		while (index<nbrIndex) {
			if (this.house.getTabComponentAt(index)==tab)
				return index;
			index++;
		}
		// Return tab not found
		return -1;
	}

	private String getName(String path) {
		if (path==null)
			return "";
		int i = path.lastIndexOf("/");
		if (i==-1)
			i = path.lastIndexOf("\\");
		if (i!=-1)
			return path.substring(i+1);
		return path;
	}

	private class TreeListener extends MouseAdapter {
		@Override
		public void mousePressed(MouseEvent event) {
			boolean isClickCountMatches = (event.getClickCount()==1&&luytenPrefs.isSingleClickOpenEnabled())
					||(event.getClickCount()==2&&!luytenPrefs.isSingleClickOpenEnabled());
			if (!isClickCountMatches)
				return;

			if (!SwingUtilities.isLeftMouseButton(event))
				return;

			final TreePath trp = tree.getPathForLocation(event.getX(), event.getY());
			if (trp==null)
				return;

			Object lastPathComponent = trp.getLastPathComponent();
			boolean isLeaf = (lastPathComponent instanceof TreeNode&&((TreeNode) lastPathComponent).isLeaf());
			if (!isLeaf)
				return;

			new Thread() {
				public void run() {
					openEntryByTreePath(trp);
				}
			}.start();
		}
	}

	private void openEntryByTreePath(TreePath trp) {
		String name = "";
		String path = "";
		try {
			bar.setVisible(true);
			if (trp.getPathCount()>1) {
				for (int i = 1; i<trp.getPathCount(); i++) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) trp.getPathComponent(i);
					TreeNodeUserObject userObject = (TreeNodeUserObject) node.getUserObject();
					if (i==trp.getPathCount()-1) {
						name = userObject.getOriginalName();
					} else {
						path = path+userObject.getOriginalName()+"/";
					}
				}
				path = path+name;

				if (file.getName().endsWith(".jar")||file.getName().endsWith(".zip")) {
					if (state==null) {
						JarFile jfile = new JarFile(file);
						ITypeLoader jarLoader = new JarTypeLoader(jfile);

						typeLoader.getTypeLoaders().add(jarLoader);
						state = new State(file.getCanonicalPath(), file, jfile, jarLoader);
					}

					JarEntry entry = state.jarFile.getJarEntry(path);
					if (entry==null) {
						throw new FileEntryNotFoundException();
					}
					if (entry.getSize()>MAX_UNPACKED_FILE_SIZE_BYTES) {
						throw new TooLargeFileException(entry.getSize());
					}
					String entryName = entry.getName();
					if (entryName.endsWith(".class")) {
						label.setText("Extracting: "+name);
						String internalName = StringUtilities.removeRight(entryName, ".class");
						TypeReference type = metadataSystem.lookupType(internalName);
						extractClassToTextPane(type, name, path, null);
					} else {
						label.setText("Opening: "+name);
						try (InputStream in = state.jarFile.getInputStream(entry);) {
							extractSimpleFileEntryToTextPane(in, name, path);
						}
					}
				}
			} else {
				name = file.getName();
				path = file.getPath().replaceAll("\\\\", "/");
				if (file.length()>MAX_UNPACKED_FILE_SIZE_BYTES) {
					throw new TooLargeFileException(file.length());
				}
				if (name.endsWith(".class")) {
					label.setText("Extracting: "+name);
					TypeReference type = metadataSystem.lookupType(path);
					extractClassToTextPane(type, name, path, null);
				} else {
					label.setText("Opening: "+name);
					try (InputStream in = new FileInputStream(file);) {
						extractSimpleFileEntryToTextPane(in, name, path);
					}
				}
			}
			label.setText("Complete");
		} catch (FileEntryNotFoundException e) {
			label.setText("File not found: "+name);
		} catch (FileIsBinaryException e) {
			label.setText("Binary resource: "+name);
		} catch (TooLargeFileException e) {
			label.setText("File is too large: "+name+" - size: "+e.getReadableFileSize());
		} catch (Exception e) {
			label.setText("Cannot open: "+name);
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e.toString(), "Error!", JOptionPane.ERROR_MESSAGE);
		} finally {
			bar.setVisible(false);
		}
	}

	private void extractClassToTextPane(TypeReference type, String tabTitle, String path, String navigatonLink) throws Exception {
		// Check parameters
		if (tabTitle==null||tabTitle.trim().isEmpty()||path==null)
			throw new FileEntryNotFoundException();
		// Resolve type definition
		TypeDefinition resolvedType = null;
		if (type==null||((resolvedType = type.resolve())==null))
			throw new Exception("Unable to resolve type.");
		// Check each already opened files
		for (OpenFile openFile : hmap) {
			// Check if path match
			if (path.equals(openFile.path)) {
				// Check if type changed or content is invalid
				if (!type.equals(openFile.getType())||!openFile.isContentValid()) {
					openFile.invalidateContent();
					openFile.setDecompilerReferences(metadataSystem, settings, decompilationOptions);
					openFile.setType(resolvedType);
					openFile.resetScrollPosition();
					openFile.decompile();
				}
				openFile.setInitialNavigationLink(navigatonLink);
				// Select related tab
				this.addOrSwitchToTab(openFile);
				return;
			}
		}
		// Create new opened file
		OpenFile open = new OpenFile(tabTitle, path, this.theme, this.mainWindow);
		open.setDecompilerReferences(this.metadataSystem, this.settings, this.decompilationOptions);
		open.setType(resolvedType);
		open.setInitialNavigationLink(navigatonLink);
		open.decompile();
		this.hmap.add(open);
		// Add opened file editor tab
		this.addOrSwitchToTab(open);
	}

	private void extractSimpleFileEntryToTextPane(InputStream inputStream, String tabTitle, String path) throws Exception {
		if (inputStream==null||tabTitle==null||tabTitle.trim().length()<1||path==null) {
			throw new FileEntryNotFoundException();
		}
		OpenFile sameTitledOpen = null;
		for (OpenFile nextOpen : hmap) {
			if (tabTitle.equals(nextOpen.name)) {
				sameTitledOpen = nextOpen;
				break;
			}
		}
		if (sameTitledOpen!=null&&path.equals(sameTitledOpen.path)) {
			addOrSwitchToTab(sameTitledOpen);
			return;
		}

		// build tab content
		StringBuilder sb = new StringBuilder();
		long nonprintableCharactersCount = 0;
		try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream); BufferedReader reader = new BufferedReader(inputStreamReader);) {
			String line;
			while ((line = reader.readLine())!=null) {
				sb.append(line).append("\n");

				for (byte nextByte : line.getBytes()) {
					if (nextByte<=0) {
						nonprintableCharactersCount++;
					}
				}

			}
		}

		// guess binary or text
		String extension = "."+tabTitle.replaceAll("^[^\\.]*$", "").replaceAll("[^\\.]*\\.", "");
		boolean isTextFile = (OpenFile.WELL_KNOWN_TEXT_FILE_EXTENSIONS.contains(extension)||nonprintableCharactersCount<sb.length()/5);
		if (!isTextFile) {
			throw new FileIsBinaryException();
		}

		// open tab
		if (sameTitledOpen!=null) {
			sameTitledOpen.path = path;
			sameTitledOpen.setDecompilerReferences(metadataSystem, settings, decompilationOptions);
			sameTitledOpen.resetScrollPosition();
			sameTitledOpen.setContent(sb.toString());
			addOrSwitchToTab(sameTitledOpen);
		} else {
			OpenFile open = new OpenFile(tabTitle, path, theme, mainWindow);
			open.setDecompilerReferences(metadataSystem, settings, decompilationOptions);
			open.setContent(sb.toString());
			hmap.add(open);
			addOrSwitchToTab(open);
		}
	}

	private class TabChangeListener implements ChangeListener {
		@Override
		public void stateChanged(ChangeEvent e) {
			int selectedIndex = house.getSelectedIndex();
			if (selectedIndex<0) {
				return;
			}
			for (OpenFile open : hmap) {
				if (house.indexOfTab(open.name)==selectedIndex) {

					if (open.getType()!=null&&!open.isContentValid()) {
						updateOpenClass(open);
						break;
					}

				}
			}
		}
	}

	public void updateOpenClasses() {
		// invalidate all open classes (update will hapen at tab change)
		for (OpenFile open : hmap) {
			if (open.getType()!=null) {
				open.invalidateContent();
			}
		}
		// update the current open tab - if it is a class
		for (OpenFile open : hmap) {
			if (open.getType()!=null&&isTabInForeground(open)) {
				updateOpenClass(open);
				break;
			}
		}
	}

	private void updateOpenClass(final OpenFile open) {
		if (open.getType()==null) {
			return;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					bar.setVisible(true);
					label.setText("Extracting: "+open.name);
					open.invalidateContent();
					open.decompile();
					label.setText("Complete");
				} catch (Exception e) {
					label.setText("Error, cannot update: "+open.name);
				} finally {
					bar.setVisible(false);
				}
			}
		}).start();
	}

	private boolean isTabInForeground(OpenFile open) {
		String title = open.name;
		int selectedIndex = house.getSelectedIndex();
		return (selectedIndex>=0&&selectedIndex==house.indexOfTab(title));
	}

	private final class State implements AutoCloseable {
		private final String key;
		private final File file;
		final JarFile jarFile;
		final ITypeLoader typeLoader;

		private State(String key, File file, JarFile jarFile, ITypeLoader typeLoader) {
			this.key = VerifyArgument.notNull(key, "key");
			this.file = VerifyArgument.notNull(file, "file");
			this.jarFile = jarFile;
			this.typeLoader = typeLoader;
		}

		@Override
		public void close() {
			if (typeLoader!=null) {
				Model.this.typeLoader.getTypeLoaders().remove(typeLoader);
			}
			Closer.tryClose(jarFile);
		}

		@SuppressWarnings("unused")
		public File getFile() {
			return file;
		}

		@SuppressWarnings("unused")
		public String getKey() {
			return key;
		}
	}

	public DefaultMutableTreeNode loadNodesByNames(DefaultMutableTreeNode node, List<String> originalNames) {
		List<TreeNodeUserObject> args = new ArrayList<>();
		for (String originalName : originalNames) {
			args.add(new TreeNodeUserObject(originalName));
		}
		return loadNodesByUserObj(node, args);
	}

	public DefaultMutableTreeNode loadNodesByUserObj(DefaultMutableTreeNode node, List<TreeNodeUserObject> args) {
		if (args.size()>0) {
			TreeNodeUserObject name = args.remove(0);
			DefaultMutableTreeNode nod = getChild(node, name);
			if (nod==null)
				nod = new DefaultMutableTreeNode(name);
			node.add(loadNodesByUserObj(nod, args));
		}
		return node;
	}

	@SuppressWarnings("unchecked")
	public DefaultMutableTreeNode getChild(DefaultMutableTreeNode node, TreeNodeUserObject name) {
		Enumeration<DefaultMutableTreeNode> entry = node.children();
		while (entry.hasMoreElements()) {
			DefaultMutableTreeNode nods = entry.nextElement();
			if (((TreeNodeUserObject) nods.getUserObject()).getOriginalName().equals(name.getOriginalName())) {
				return nods;
			}
		}
		return null;
	}

	public void loadFile(File file) {
		if (open)
			closeFile();
		this.file = file;
		loadTree();
	}

	public void updateTree() {
		TreeUtil treeUtil = new TreeUtil(tree);
		treeExpansionState = treeUtil.getExpansionState();
		loadTree();
	}

	public void loadTree() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (file==null) {
						return;
					}
					tree.setModel(new DefaultTreeModel(null));

					if (file.length()>MAX_JAR_FILE_SIZE_BYTES) {
						throw new TooLargeFileException(file.length());
					}
					if (file.getName().endsWith(".zip")||file.getName().endsWith(".jar")) {
						JarFile jfile;
						jfile = new JarFile(file);
						label.setText("Loading: "+jfile.getName());
						bar.setVisible(true);

						JarEntryFilter jarEntryFilter = new JarEntryFilter(jfile);
						List<String> mass = null;
						if (luytenPrefs.isFilterOutInnerClassEntries()) {
							mass = jarEntryFilter.getEntriesWithoutInnerClasses();
						} else {
							mass = jarEntryFilter.getAllEntriesFromJar();
						}
						tree.setModel(buildTree(mass));

						if (state==null) {
							ITypeLoader jarLoader = new JarTypeLoader(jfile);
							typeLoader.getTypeLoaders().add(jarLoader);
							state = new State(file.getCanonicalPath(), file, jfile, jarLoader);
						}
						open = true;
						label.setText("Complete");
					} else {
						TreeNodeUserObject topNodeUserObject = new TreeNodeUserObject(getName(file.getName()));
						final DefaultMutableTreeNode top = new DefaultMutableTreeNode(topNodeUserObject);
						tree.setModel(new DefaultTreeModel(top));
						settings.setTypeLoader(new InputTypeLoader());
						open = true;
						label.setText("Complete");

						// open it automatically
						new Thread() {
							public void run() {
								TreePath trp = new TreePath(top.getPath());
								openEntryByTreePath(trp);
							};
						}.start();
					}

					if (treeExpansionState!=null) {
						try {
							TreeUtil treeUtil = new TreeUtil(tree);
							treeUtil.restoreExpanstionState(treeExpansionState);
						} catch (Exception exc) {
							exc.printStackTrace();
						}
					}
				} catch (TooLargeFileException e) {
					label.setText("File is too large: "+file.getName()+" - size: "+e.getReadableFileSize());
					closeFile();
				} catch (Exception e1) {
					e1.printStackTrace();
					label.setText("Cannot open: "+file.getName());
					closeFile();
				} finally {
					mainWindow.onFileLoadEnded(file, open);
					bar.setVisible(false);
				}
			}

		}).start();
	}

	/**
	 * Build tree from JAR entries.
	 * 
	 * @param jarEntries
	 *            The JAR entries to build tree.
	 * @return The build tree model.
	 */
	private TreeModel buildTree(List<String> jarEntries) {
		// Create root node according preferences
		TreeNode rootNode;
		if (this.luytenPrefs.isPackageExplorerStyle()) {
			rootNode = buildFlatTreeNode(jarEntries);
		} else {
			rootNode = buildHierarchicalTreeNode(jarEntries);
		}
		// Create tree model from root node
		return new DefaultTreeModel(rootNode);
	}

	/**
	 * Build recursively resource node from paths.
	 * 
	 * @param node
	 *            The node from which to build paths.
	 * @param depth
	 *            The current recursive depth (from <code>0</code> to <code>entryPaths.length-1</code>).
	 * @param entryPaths
	 *            The entry paths to build resource nodes.
	 */
	private void buildRecursiveResourceNode(ResourceNode node, int depth, String[] entryPaths) {
		// Get entry path of the depth
		String entryPath = entryPaths[depth];
		// Ensure related child node exits
		ResourceNode pathNode = node.getChild(entryPath);
		if (pathNode==null) {
			pathNode = new ResourceNode(entryPath);
			node.addChild(pathNode);
		}
		// Go deeper in path
		depth++;
		if (depth<entryPaths.length)
			this.buildRecursiveResourceNode(pathNode, depth, entryPaths);
	}

	/**
	 * Convert recursively resource node to tree node.
	 * 
	 * @param resourceNode
	 *            The resource node to convert.
	 * @param treeNode
	 *            The converted tree node.
	 */
	private void convertRecursiveTreeNode(ResourceNode resourceNode, DefaultMutableTreeNode treeNode) {
		// Convert each children
		for (ResourceNode childNode : resourceNode.getChildren()) {
			// Create related child tree node
			DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode(new TreeNodeUserObject(childNode.getName()));
			// Append child tree node
			treeNode.add(childTreeNode);
			// Convert sub children
			this.convertRecursiveTreeNode(childNode, childTreeNode);
		}
	}

	/**
	 * Build a hierarchical tree node.
	 * 
	 * @param jarEntries
	 *            The JAR entries to build tree.
	 * @return The hierarchical root tree node.
	 */
	private TreeNode buildHierarchicalTreeNode(List<String> jarEntries) {
		// Get root name
		String fileName = this.getName(this.file.getName());
		// Create resource nodes
		ResourceNode rootNode = new ResourceNode(getName(file.getName()));
		for (String jarEntry : jarEntries) {
			String[] entryPaths = jarEntry.split("/");
			this.buildRecursiveResourceNode(rootNode, 0, entryPaths);
		}
		// Sort resource nodes
		rootNode.sort();
		// Create tree nodes
		DefaultMutableTreeNode rootTreeNode = new DefaultMutableTreeNode(new TreeNodeUserObject(fileName));
		this.convertRecursiveTreeNode(rootNode, rootTreeNode);
		// Return root tree node
		return rootTreeNode;
	}

	private TreeNode buildFlatTreeNode(List<String> mass) {
		TreeNodeUserObject topNodeUserObject = new TreeNodeUserObject(getName(file.getName()));
		DefaultMutableTreeNode top = new DefaultMutableTreeNode(topNodeUserObject);

		TreeMap<String, TreeSet<String>> packages = new TreeMap<>();
		HashSet<String> classContainingPackageRoots = new HashSet<>();

		Comparator<String> sortByFileExtensionsComparator = new Comparator<String>() {
			// (assertion: mass does not contain null elements)
			@Override
			public int compare(String o1, String o2) {
				int comp = o1.replaceAll("[^\\.]*\\.", "").compareTo(o2.replaceAll("[^\\.]*\\.", ""));
				if (comp!=0)
					return comp;
				return o1.compareTo(o2);
			}
		};

		for (String entry : mass) {
			String packagePath = "";
			String packageRoot = "";
			if (entry.contains("/")) {
				packagePath = entry.replaceAll("/[^/]*$", "");
				packageRoot = entry.replaceAll("/.*$", "");
			}
			String packageEntry = entry.replace(packagePath+"/", "");
			if (!packages.containsKey(packagePath)) {
				packages.put(packagePath, new TreeSet<String>(sortByFileExtensionsComparator));
			}
			packages.get(packagePath).add(packageEntry);
			if (!entry.startsWith("META-INF")&&packageRoot.trim().length()>0&&entry.matches(".*\\.(class|java|prop|properties)$")) {
				classContainingPackageRoots.add(packageRoot);
			}
		}

		// META-INF comes first -> not flat
		for (String packagePath : packages.keySet()) {
			if (packagePath.startsWith("META-INF")) {
				List<String> packagePathElements = Arrays.asList(packagePath.split("/"));
				for (String entry : packages.get(packagePath)) {
					ArrayList<String> list = new ArrayList<>(packagePathElements);
					list.add(entry);
					loadNodesByNames(top, list);
				}
			}
		}

		// real packages: path starts with a classContainingPackageRoot -> flat
		for (String packagePath : packages.keySet()) {
			String packageRoot = packagePath.replaceAll("/.*$", "");
			if (classContainingPackageRoots.contains(packageRoot)) {
				for (String entry : packages.get(packagePath)) {
					ArrayList<TreeNodeUserObject> list = new ArrayList<>();
					list.add(new TreeNodeUserObject(packagePath, packagePath.replaceAll("/", ".")));
					list.add(new TreeNodeUserObject(entry));
					loadNodesByUserObj(top, list);
				}
			}
		}

		// the rest, not real packages but directories -> not flat
		for (String packagePath : packages.keySet()) {
			String packageRoot = packagePath.replaceAll("/.*$", "");
			if (!classContainingPackageRoots.contains(packageRoot)&&!packagePath.startsWith("META-INF")&&packagePath.length()>0) {
				List<String> packagePathElements = Arrays.asList(packagePath.split("/"));
				for (String entry : packages.get(packagePath)) {
					ArrayList<String> list = new ArrayList<>(packagePathElements);
					list.add(entry);
					loadNodesByNames(top, list);
				}
			}
		}

		// the default package -> not flat
		String packagePath = "";
		if (packages.containsKey(packagePath)) {
			for (String entry : packages.get(packagePath)) {
				ArrayList<String> list = new ArrayList<>();
				list.add(entry);
				loadNodesByNames(top, list);
			}
		}
		return top;
	}

	public void closeFile() {
		for (OpenFile co : hmap) {
			int pos = house.indexOfTab(co.name);
			if (pos>=0)
				house.remove(pos);
			co.close();
		}

		final State oldState = state;
		Model.this.state = null;
		if (oldState!=null) {
			Closer.tryClose(oldState);
		}

		hmap.clear();
		tree.setModel(new DefaultTreeModel(null));
		metadataSystem = new MetadataSystem(typeLoader);
		file = null;
		treeExpansionState = null;
		open = false;
		mainWindow.onFileLoadEnded(file, open);
	}

	public void changeTheme(String xml) {
		InputStream in = getClass().getResourceAsStream(LuytenPreferences.THEME_XML_PATH+xml);
		try {
			if (in!=null) {
				theme = Theme.load(in);
				for (OpenFile f : hmap) {
					theme.apply(f.textArea);
				}
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			JOptionPane.showMessageDialog(null, e1.toString(), "Error!", JOptionPane.ERROR_MESSAGE);
		}
	}

	public File getOpenedFile() {
		File openedFile = null;
		if (file!=null&&open) {
			openedFile = file;
		}
		if (openedFile==null) {
			label.setText("No open file");
		}
		return openedFile;
	}

	public String getCurrentTabTitle() {
		String tabTitle = null;
		try {
			int pos = house.getSelectedIndex();
			if (pos>=0) {
				tabTitle = house.getTitleAt(pos);
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		if (tabTitle==null) {
			label.setText("No open tab");
		}
		return tabTitle;
	}

	public RSyntaxTextArea getCurrentTextArea() {
		RSyntaxTextArea currentTextArea = null;
		try {
			int pos = house.getSelectedIndex();
			if (pos>=0) {
				RTextScrollPane co = (RTextScrollPane) house.getComponentAt(pos);
				currentTextArea = (RSyntaxTextArea) co.getViewport().getView();
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		if (currentTextArea==null) {
			label.setText("No open tab");
		}
		return currentTextArea;
	}

	public void startWarmUpThread() {
		new Thread() {
			public void run() {
				try {
					Thread.sleep(500);
					String internalName = FindBox.class.getName();
					TypeReference type = metadataSystem.lookupType(internalName);
					TypeDefinition resolvedType = null;
					if ((type==null)||((resolvedType = type.resolve())==null)) {
						return;
					}
					StringWriter stringwriter = new StringWriter();
					settings.getLanguage().decompileType(resolvedType, new PlainTextOutput(stringwriter), decompilationOptions);
					String decompiledSource = stringwriter.toString();
					OpenFile open = new OpenFile(internalName, "*/"+internalName, theme, mainWindow);
					open.setContent(decompiledSource);
					JTabbedPane pane = new JTabbedPane();
					pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
					pane.addTab("title", open.scrollPane);
					pane.setSelectedIndex(pane.indexOfTab("title"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}

	public void navigateTo(final String uniqueStr) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (uniqueStr==null)
					return;
				String[] linkParts = uniqueStr.split("\\|");
				if (linkParts.length<=1)
					return;
				String destinationTypeStr = linkParts[1];
				try {
					bar.setVisible(true);
					label.setText("Navigating: "+destinationTypeStr.replaceAll("/", "."));

					TypeReference type = metadataSystem.lookupType(destinationTypeStr);
					if (type==null)
						throw new RuntimeException("Cannot lookup type: "+destinationTypeStr);
					TypeDefinition typeDef = type.resolve();
					if (typeDef==null)
						throw new RuntimeException("Cannot resolve type: "+destinationTypeStr);

					String tabTitle = typeDef.getName()+".class";
					extractClassToTextPane(typeDef, tabTitle, destinationTypeStr, uniqueStr);

					label.setText("Complete");
				} catch (Exception e) {
					label.setText("Cannot navigate: "+destinationTypeStr.replaceAll("/", "."));
					e.printStackTrace();
				} finally {
					bar.setVisible(false);
				}
			}
		}).start();
	}
}
