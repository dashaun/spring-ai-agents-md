package org.springframework.ai.autoconfigure.agents.discovery;

import java.util.Objects;

import org.springframework.ai.autoconfigure.agents.parser.AgentsMdDocument;

/**
 * An applicable {@code AGENTS.md} document and its source location.
 *
 * @param location a display-safe source location
 * @param document the complete Markdown document
 */
public record AgentsMdResource(String location, AgentsMdDocument document) {

	public AgentsMdResource {
		Objects.requireNonNull(location, "location must not be null");
		Objects.requireNonNull(document, "document must not be null");
	}

}
