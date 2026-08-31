package org.springframework.ai.autoconfigure.agents.parser;

import java.util.Objects;

/**
 * An {@code AGENTS.md} document represented as standard Markdown.
 *
 * @param content the complete Markdown document
 */
public record AgentsMdDocument(String content) {

	public AgentsMdDocument {
		Objects.requireNonNull(content, "content must not be null");
	}

	/**
	 * Return the document unchanged for use as system-prompt context.
	 * @return the complete Markdown document
	 */
	public String toSystemPromptContext() {
		return this.content;
	}

}
