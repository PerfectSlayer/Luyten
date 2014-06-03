package com.modcrafting.luyten.model;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;

import com.modcrafting.luyten.model.tree.ResourceNode;

/**
 * This class represents a resource visitor.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class ResourceVisitor extends SimpleFileVisitor<Path> {
	/** The inner class pattern. */
	private static final Pattern INNER_CLASS_PATTERN = Pattern.compile("^(NamelessClass)?[0-9]+\\.class$");
	/** The root resource node. */
	private final ResourceNode rootResourceNode;
	/** The current visit node. */
	private ResourceNode currentNode;

	/**
	 * Constructor.
	 * 
	 * @param rootResourceNode
	 *            The root resource node.
	 */
	public ResourceVisitor(ResourceNode rootResourceNode) {
		// Store the root resource node
		this.rootResourceNode = rootResourceNode;
		// Initialize current node
		this.currentNode = this.rootResourceNode;
	}

	@Override
	public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
		// Skip root
		if (dir.getNameCount()==0)
			return FileVisitResult.CONTINUE;
		// Create directory node
		String directoryName = dir.getFileName().toString();
		if (directoryName.endsWith("/"))
			directoryName = directoryName.substring(0, directoryName.length()-1);
		ResourceNode directoryNode = new ResourceNode(directoryName, true);
		// Add node to current node
		this.currentNode.addChild(directoryNode);
		// Set directory node as current node
		this.currentNode = directoryNode;
		// Continue visit
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		// Get resource name
		String resourceName = file.getFileName().toString();
		// Check class file
		if (resourceName.endsWith(".class")) {
			// Check inner classes delimiter
			int index = resourceName.lastIndexOf("$");
			if (index != -1) {
				// Get inner class name
				String innerClassName = resourceName.substring(index+1);
				// Check anonymous class name
				if (ResourceVisitor.INNER_CLASS_PATTERN.matcher(innerClassName).matches())
					return FileVisitResult.CONTINUE;
			}
		}
		// Create resource node
		ResourceNode resourceNode = new ResourceNode(resourceName, false);
		// Add resource node to current node
		this.currentNode.addChild(resourceNode);
		// Continue visit
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		// Set parent node as current node
		this.currentNode = this.currentNode.getParent();
		// Continue visit
		return FileVisitResult.CONTINUE;
	}
}