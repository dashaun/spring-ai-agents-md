package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.util.Assert;

/**
 * Mutable active-path state shared between an AGENTS.md advisor and filesystem tools
 * using the same {@code ChatClient}.
 */
public final class AgentsMdActivePath {

	private final AtomicReference<Path> path = new AtomicReference<>();

	/**
	 * Update the active file or directory.
	 * @param path active path reported by a filesystem tool
	 */
	public void update(Path path) {
		Assert.notNull(path, "Path must not be null");
		this.path.set(path.normalize());
	}

	/** Return the current active path, when a tool has reported one. */
	public Optional<Path> get() {
		return Optional.ofNullable(this.path.get());
	}

	/** Clear the tool-propagated active path. */
	public void clear() {
		this.path.set(null);
	}

}
