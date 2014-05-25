package com.modcrafting.luyten.model.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class represents a resource node.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class ResourceNode implements Comparable<ResourceNode> {
	/** The node name. */
	private final String name;
	/** The node package status. */
	private final boolean isPackage;
	/** The node children. */
	private final List<ResourceNode> children;

	/**
	 * Constructor.
	 * 
	 * @param name
	 *            The resource node name.
	 */
	public ResourceNode(String name) {
		this.name = name;
		this.isPackage = !name.contains(".");
		this.children = new ArrayList<>();
	}

	/**
	 * Get the node name.
	 * 
	 * @return The node name.
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Add child node.
	 * 
	 * @param child
	 *            The child node to add.
	 */
	public void addChild(ResourceNode child) {
		this.children.add(child);
	}

	/**
	 * Get a child from its name.
	 * 
	 * @param childName
	 *            The child name to get.
	 * @return The child with the given name, <code>null</code> if does not exist.
	 */
	public ResourceNode getChild(String childName) {
		for (ResourceNode child : this.children) {
			if (child.getName().equals(childName))
				return child;
		}
		return null;
	}

	/**
	 * Get the node children.
	 * 
	 * @return The node children.
	 */
	public List<ResourceNode> getChildren() {
		return this.children;
	}

	/**
	 * Sort the node and its children.
	 */
	public void sort() {
		Collections.sort(this.children);
		for (ResourceNode child : this.children)
			child.sort();
	}

	@Override
	public String toString() {
		return this.name;
	}

	/*
	 * Comparable.
	 */

	@Override
	public int compareTo(ResourceNode resourceNode) {
		// Compare package status
		if (this.isPackage&&!resourceNode.isPackage)
			return -1;
		if (!this.isPackage&&resourceNode.isPackage)
			return 1;
		// Compare resource name
		return this.name.compareTo(resourceNode.name);
	}
}