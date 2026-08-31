package org.springframework.ai.autoconfigure.agents.discovery;

import java.nio.file.Path;

/** Resolve the {@code AGENTS.md} instructions applicable to a target path. */
@FunctionalInterface
public interface AgentsMdResolver {

	/**
	 * Resolve instructions for a file or directory.
	 * @param target target file or directory
	 * @return applicable instructions
	 */
	AgentsMdResolution resolve(Path target);

}
