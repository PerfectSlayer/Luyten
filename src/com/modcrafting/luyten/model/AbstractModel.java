package com.modcrafting.luyten.model;

import java.nio.file.Path;

import com.modcrafting.luyten.model.tree.ResourceNode;

/**
 * This class represents a base class for model implementation.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public abstract class AbstractModel {
	/** The model path. */
	protected Path path;

	/**
	 * Constructor.
	 * 
	 * @param path
	 *            The model path.
	 */
	public AbstractModel(Path path) {
		this.path = path;
	}

	/**
	 * Get the resources of the model.
	 * 
	 * @return The resources of the model.
	 */
	public abstract ResourceNode getResources();
}