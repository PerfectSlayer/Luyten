package com.modcrafting.luyten.view.editor;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * This class represents is an utility class for file editor syntax.
 * 
 * @author Perfect Slayer (bruce.bujon@gmail.com)
 * 
 */
public class FileEditorSyntax {
	/**
	 * Get syntax from resource name.
	 * 
	 * @param resourceName
	 *            The resource name to get syntax.
	 * @return The syntax of the given resource.
	 */
	public static String getSyntax(String resourceName) {
		resourceName = resourceName.toLowerCase();
		if (resourceName.endsWith(".class")||resourceName.endsWith(".java"))
			return SyntaxConstants.SYNTAX_STYLE_JAVA;
		if (resourceName.endsWith(".xml")||resourceName.endsWith(".rss")||resourceName.endsWith(".project")||resourceName.endsWith(".classpath"))
			return SyntaxConstants.SYNTAX_STYLE_XML;
		if (resourceName.endsWith(".h"))
			return SyntaxConstants.SYNTAX_STYLE_C;
		if (resourceName.endsWith(".sql"))
			return SyntaxConstants.SYNTAX_STYLE_SQL;
		if (resourceName.endsWith(".js"))
			return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
		if (resourceName.endsWith(".php")||resourceName.endsWith(".php5")||resourceName.endsWith(".phtml"))
			return SyntaxConstants.SYNTAX_STYLE_PHP;
		if (resourceName.endsWith(".html")||resourceName.endsWith(".htm")||resourceName.endsWith(".xhtm")||resourceName.endsWith(".xhtml"))
			return SyntaxConstants.SYNTAX_STYLE_HTML;
		if (resourceName.endsWith(".js"))
			return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
		if (resourceName.endsWith(".lua"))
			return SyntaxConstants.SYNTAX_STYLE_LUA;
		if (resourceName.endsWith(".bat"))
			return SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
		if (resourceName.endsWith(".pl"))
			return SyntaxConstants.SYNTAX_STYLE_PERL;
		if (resourceName.endsWith(".sh"))
			return SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
		if (resourceName.endsWith(".css"))
			return SyntaxConstants.SYNTAX_STYLE_CSS;
		if (resourceName.endsWith(".json"))
			return SyntaxConstants.SYNTAX_STYLE_JSON;
		if (resourceName.endsWith(".txt"))
			return SyntaxConstants.SYNTAX_STYLE_NONE;
		if (resourceName.endsWith(".rb"))
			return SyntaxConstants.SYNTAX_STYLE_RUBY;
		if (resourceName.endsWith(".make")||resourceName.endsWith(".mak"))
			return SyntaxConstants.SYNTAX_STYLE_MAKEFILE;
		if (resourceName.endsWith(".py"))
			return SyntaxConstants.SYNTAX_STYLE_PYTHON;
		if (resourceName.endsWith(".prop")||resourceName.endsWith(".properties"))
			return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
		return SyntaxConstants.SYNTAX_STYLE_NONE;
	}

}