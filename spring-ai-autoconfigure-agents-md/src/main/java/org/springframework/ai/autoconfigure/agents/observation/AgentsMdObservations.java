package org.springframework.ai.autoconfigure.agents.observation;

/**
 * Stable names used by AGENTS.md observations.
 */
public final class AgentsMdObservations {

	/** Observation emitted when the advisor augments a request. */
	public static final String ADVISOR = "spring.ai.agents.md.advisor";

	/** Human-readable contextual name for advisor observations. */
	public static final String ADVISOR_CONTEXTUAL_NAME = "agents-md advisor";

	/** Low-cardinality key describing whether the document contains instructions. */
	public static final String DOCUMENT_STATE = "spring.ai.agents.md.document.state";

	/** Low-cardinality key describing the number of applicable documents. */
	public static final String DOCUMENT_COUNT = "spring.ai.agents.md.document.count";

	/** Low-cardinality key describing whether resolution reached a safety limit. */
	public static final String RESOLUTION_OUTCOME = "spring.ai.agents.md.resolution.outcome";

	/** Distribution summary recording the injected context size in characters. */
	public static final String CONTEXT_SIZE = "spring.ai.agents.md.context.size";

	/** Tag value used when instructions are available. */
	public static final String DOCUMENT_PRESENT = "present";

	/** Tag value used when no instructions are available. */
	public static final String DOCUMENT_EMPTY = "empty";

	/** Tag value used when no documents apply. */
	public static final String DOCUMENT_COUNT_ZERO = "zero";

	/** Tag value used when exactly one document applies. */
	public static final String DOCUMENT_COUNT_ONE = "one";

	/** Tag value used when more than one document applies. */
	public static final String DOCUMENT_COUNT_MULTIPLE = "multiple";

	private AgentsMdObservations() {
	}

}
