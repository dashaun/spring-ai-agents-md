package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;

/**
 * Mutable active-path state shared between an AGENTS.md advisor and filesystem tools
 * using the same {@code ChatClient}.
 *
 * <p>
 * The state is shared across requests on the same {@code ChatClient} and is intended for
 * sequential tool loops. Concurrent or cross-thread requests may leak the active path.
 * When the state is accessed from a different thread than the previous access, a warning
 * is logged once so the misuse is visible instead of silent.
 */
public final class AgentsMdActivePath {

	private static final Logger logger = LoggerFactory.getLogger(AgentsMdActivePath.class);

	private final AtomicReference<Path> path = new AtomicReference<>();

	private final AtomicReference<Thread> lastAccessor = new AtomicReference<>();

	private final AtomicBoolean warned = new AtomicBoolean();

	/**
	 * Update the active file or directory.
	 * @param path active path reported by a filesystem tool
	 */
	public void update(Path path) {
		Assert.notNull(path, "Path must not be null");
		checkConcurrentAccess();
		this.path.set(path.normalize());
	}

	/** Return the current active path, when a tool has reported one. */
	public Optional<Path> get() {
		checkConcurrentAccess();
		return Optional.ofNullable(this.path.get());
	}

	/** Clear the tool-propagated active path. */
	public void clear() {
		checkConcurrentAccess();
		this.path.set(null);
	}

	private void checkConcurrentAccess() {
		Thread current = Thread.currentThread();
		Thread previous = this.lastAccessor.getAndSet(current);
		if (previous != null && previous != current && this.warned.compareAndSet(false, true)) {
			logger.warn("AGENTS.md active-path state is shared across threads; concurrent or cross-thread requests "
					+ "may leak the active path. Pass an explicit target with AgentsMdAdvisorParams.target(...) "
					+ "for isolated requests.");
		}
	}

}
