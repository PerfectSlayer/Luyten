package com.modcrafting.luyten.model;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import com.modcrafting.luyten.model.tree.ResourceNode;

/**
 * This class represents a jar file model.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class JarModel extends AbstractModel {
	/** The root resource node. */
	private ResourceNode rootResourceNode;

	/**
	 * Constructor.
	 * 
	 * @param path
	 *            The path to the JAR.
	 * @throws Exception
	 *             Throws exception if the model could not be created.
	 */
	public JarModel(Path path) throws Exception {
		super(path);
		// Create ZIP file system
		String originalUri = path.toUri().toString();
		String jarUri = "jar:file:"+originalUri.substring(5);
		FileSystem fileSystem = null;
		try {
			URI uri = new URI(jarUri);
			try {
				fileSystem = FileSystems.getFileSystem(uri);
			} catch (FileSystemNotFoundException exception) {
				fileSystem = FileSystems.newFileSystem(uri, new HashMap<String, String>(), null);
			}
		} catch (IOException|URISyntaxException exception) {
			throw new Exception("Unable to create JAR file system.", exception);
		}
		// Create resource nodes
		this.rootResourceNode = new ResourceNode(this.path.getFileName().toString(), true);
		try {
			for (Path rootPath : fileSystem.getRootDirectories()) {
				Files.walkFileTree(rootPath, new ResourceVisitor(this.rootResourceNode));
			}
		} catch (IOException exception) {
			throw new Exception("Unable to visit JAR.", exception);
		}
		this.rootResourceNode.sort();
	}

	@Override
	public ResourceNode getResources() {
		return this.rootResourceNode;
	}
}