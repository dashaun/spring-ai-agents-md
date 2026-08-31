package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.Assert;

/** Stable context keys and helpers for target-aware AGENTS.md resolution. */
public final class AgentsMdAdvisorParams {

	/** Advisor parameter for an explicit request target path. */
	public static final String TARGET_PATH = "spring.ai.agents-md.target-path";

	/** Shared state made available to filesystem tools through {@link ToolContext}. */
	public static final String ACTIVE_PATH = "spring.ai.agents-md.active-path";

	private AgentsMdAdvisorParams() {
	}

	/**
	 * Configure an explicit target path for one request.
	 * @param target target file or directory
	 * @return Advisor configuration consumer
	 */
	public static Consumer<ChatClient.AdvisorSpec> target(Path target) {
		Assert.notNull(target, "Target must not be null");
		return spec -> spec.param(TARGET_PATH, target);
	}

	/**
	 * Propagate the path used by a filesystem tool to subsequent Advisor resolution.
	 * @param toolContext context supplied to the tool by Spring AI
	 * @param path file or directory accessed by the tool
	 */
	public static void propagateActivePath(ToolContext toolContext, Path path) {
		Assert.notNull(toolContext, "ToolContext must not be null");
		Assert.notNull(path, "Path must not be null");
		Object state = toolContext.getContext().get(ACTIVE_PATH);
		Assert.state(state instanceof AgentsMdActivePath,
				"No AGENTS.md active-path state is available; use Spring AI's auto-configured ChatClient.Builder");
		((AgentsMdActivePath) state).update(path);
	}

}
