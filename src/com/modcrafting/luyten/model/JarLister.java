package com.modcrafting.luyten.model;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * This class represents a jar file content lister utility.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class JarLister {
	/** The inner class pattern (ex: com/acme/Model$16.class). */
	private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(".*[^(/|\\\\)]+\\$[^(/|\\\\)]+$");

	/**
	 * Get all files from a jar file.
	 * 
	 * @param jarFile
	 *            The jar file to get all files.
	 * @return All files of the jar file.
	 */
	public static List<String> listFiles(JarFile jarFile) {
		return JarLister.listFiles(jarFile, true);
	}

	/**
	 * Get all files except inner class from a jar file.
	 * 
	 * @param jarFile
	 *            The jar file to get files.
	 * @param includeInnerClass
	 *            <code>true</code> to include inner class to listing, <code>false</code> otherwise.
	 * @return All files except inner class of the jar file.
	 */
	public static List<String> listFiles(JarFile jarFile, boolean includeInnerClass) {
		// Create file collection
		List<String> files = new ArrayList<>();
		// Get all jar entries
		Enumeration<JarEntry> entries = jarFile.entries();
		// Declare class collections
		Set<String> possibleInnerClasses = new HashSet<>();
		Set<String> baseClasses = new HashSet<>();
		// Check each jar entry
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			// Skip directory entry
			if (entry.isDirectory())
				continue;
			// Get entry name
			String entryName = entry.getName();
			// Check inner class filter
			if (includeInnerClass) {
				files.add(entryName);
				continue;
			}
			// Check not class file
			if (!entryName.endsWith(".class")) {
				files.add(entryName);
				continue;
			}
			// Check inner class pattern
			if (JarLister.INNER_CLASS_PATTERN.matcher(entryName).matches()) {
				possibleInnerClasses.add(entryName);
				continue;
			}
			// Add class to base class
			baseClasses.add(entryName);
			files.add(entryName);
		}
		// Check inner class filter
		if (!includeInnerClass) {
			// Check each inner class
			for (String inner : possibleInnerClasses) {
				// com/acme/Connection$Conn$1.class -> com/acme/Connection
				String innerWithoutTail = inner.replaceAll("\\$[^(/|\\\\)]+\\.class$", "");
				// Skip inner class
				if (baseClasses.contains(innerWithoutTail+".class"))
					continue;
				// Keep Badly$Named but not inner classes
				files.add(inner);
			}
		}
		// Return listed files
		return files;
	}
}