package org.springframework.ai.autoconfigure.agents.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The {@code AGENTS.md} resources applicable to a target path, ordered from broadest
 * scope to closest scope.
 *
 * @param target normalized absolute target path
 * @param resources applicable resources, broadest first and closest last
 * @param outcome resolution outcome, including whether a safety limit was reached
 * @param configuredLimit configured value of the reached limit, or zero for a complete
 * resolution
 */
public record AgentsMdResolution(Path target, List<AgentsMdResource> resources, AgentsMdResolutionOutcome outcome,
		long configuredLimit) {

	public AgentsMdResolution(Path target, List<AgentsMdResource> resources) {
		this(target, resources, AgentsMdResolutionOutcome.COMPLETE, 0);
	}

	public AgentsMdResolution(Path target, List<AgentsMdResource> resources, AgentsMdResolutionOutcome outcome) {
		this(target, resources, outcome, 0);
	}

	public AgentsMdResolution {
		Objects.requireNonNull(target, "target must not be null");
		resources = List.copyOf(Objects.requireNonNull(resources, "resources must not be null"));
		Objects.requireNonNull(outcome, "outcome must not be null");
		if (configuredLimit < 0) {
			throw new IllegalArgumentException("configuredLimit must not be negative");
		}
	}

	/**
	 * Format all applicable documents as system-prompt context. Documents retain their
	 * complete content inside an envelope that declares user and hierarchical precedence.
	 * @return system-prompt context, or an empty string when nothing applies
	 */
	public String toSystemPromptContext() {
		if (this.resources.isEmpty()) {
			return "";
		}
		StringBuilder context = new StringBuilder("# AGENTS.md instructions\n\n").append(
				"Apply these project instructions to the current task. When an explicit instruction in the current ")
			.append("user request conflicts with these instructions, follow the explicit user instruction.\n");
		if (this.resources.size() > 1) {
			context.append("\nDocuments are ordered from broadest scope to closest scope. ")
				.append("When document instructions conflict, the closest document takes precedence.\n");
		}
		for (AgentsMdResource resource : this.resources) {
			context.append("\n## ")
				.append(resource.location())
				.append("\n\n")
				.append(resource.document().toSystemPromptContext());
		}
		return context.toString();
	}

}
