package org.springframework.ai.autoconfigure.agents.observation;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolutionOutcome;

/**
 * Published once when an advisor resolution reaches an AGENTS.md safety limit.
 *
 * @param target normalized active target
 * @param outcome limit that was reached
 * @param acceptedDocumentCount number of documents retained in the context
 * @param contextSizeBytes UTF-8 size of the context added to the prompt
 * @param configuredLimit configured value of the limit that was reached
 */
public record AgentsMdLimitReachedEvent(Path target, AgentsMdResolutionOutcome outcome, int acceptedDocumentCount,
		long contextSizeBytes, long configuredLimit) {

	public AgentsMdLimitReachedEvent {
		Objects.requireNonNull(target, "target must not be null");
		Objects.requireNonNull(outcome, "outcome must not be null");
		if (outcome == AgentsMdResolutionOutcome.COMPLETE) {
			throw new IllegalArgumentException("outcome must describe a reached limit");
		}
		if (acceptedDocumentCount < 0 || contextSizeBytes < 0 || configuredLimit < 1) {
			throw new IllegalArgumentException("counts, sizes, and limits must not be negative or zero");
		}
	}

}
