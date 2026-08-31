package org.springframework.ai.autoconfigure.agents.discovery;

/** Outcome of resolving applicable AGENTS.md documents. */
public enum AgentsMdResolutionOutcome {

	/** Resolution completed without reaching a configured limit. */
	COMPLETE("complete"),

	/** Directory traversal reached the configured depth limit. */
	DEPTH_LIMIT("depth-limit"),

	/** The number of applicable documents reached the configured limit. */
	DOCUMENT_LIMIT("document-limit"),

	/** An individual or aggregate document size reached a configured limit. */
	SIZE_LIMIT("size-limit");

	private final String tagValue;

	AgentsMdResolutionOutcome(String tagValue) {
		this.tagValue = tagValue;
	}

	public String tagValue() {
		return this.tagValue;
	}

}
