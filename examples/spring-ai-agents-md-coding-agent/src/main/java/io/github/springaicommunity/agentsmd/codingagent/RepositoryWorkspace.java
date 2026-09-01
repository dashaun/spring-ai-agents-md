package io.github.springaicommunity.agentsmd.codingagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

final class RepositoryWorkspace {

	private static final Set<String> BLOCKED_SEGMENTS = Set.of(".git", ".idea", ".vscode", "target");

	private final Path root;

	private final RepositoryStewardProperties properties;

	RepositoryWorkspace(RepositoryStewardProperties properties) {
		this.properties = properties;
		try {
			this.root = properties.workspace().toRealPath();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("The configured steward workspace must be an existing directory", ex);
		}
		if (!Files.isDirectory(this.root)) {
			throw new IllegalArgumentException("The configured steward workspace must be a directory");
		}
	}

	Path root() {
		return this.root;
	}

	RepositoryStewardProperties properties() {
		return this.properties;
	}

	Path resolve(String value, boolean allowMissingLeaf) {
		if (value == null || value.isBlank()) {
			value = ".";
		}
		Path supplied = Path.of(value);
		if (supplied.isAbsolute()) {
			throw new IllegalArgumentException("Use a path relative to the steward workspace");
		}
		Path resolved = this.root.resolve(supplied).normalize();
		if (!resolved.startsWith(this.root)) {
			throw new IllegalArgumentException("Path escapes the steward workspace");
		}
		for (Path segment : this.root.relativize(resolved)) {
			if (BLOCKED_SEGMENTS.contains(segment.toString())) {
				throw new IllegalArgumentException("Path is in a protected workspace area");
			}
		}
		verifyNoSymbolicLinks(resolved, allowMissingLeaf);
		return resolved;
	}

	String display(Path path) {
		String relative = this.root.relativize(path).toString();
		return relative.isEmpty() ? "." : relative;
	}

	private void verifyNoSymbolicLinks(Path resolved, boolean allowMissingLeaf) {
		Path relative = this.root.relativize(resolved);
		Path current = this.root;
		int index = 0;
		for (Path segment : relative) {
			current = current.resolve(segment);
			index++;
			if (!Files.exists(current)) {
				if (allowMissingLeaf && index == relative.getNameCount()) {
					return;
				}
				throw new IllegalArgumentException("Path does not exist in the steward workspace " + this.root + ": "
						+ this.root.relativize(resolved));
			}
			if (Files.isSymbolicLink(current)) {
				throw new IllegalArgumentException("Symbolic links are not accessible to the steward");
			}
		}
	}

}
