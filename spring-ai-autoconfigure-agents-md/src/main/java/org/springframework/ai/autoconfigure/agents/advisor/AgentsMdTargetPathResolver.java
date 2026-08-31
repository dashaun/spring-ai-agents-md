package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClientRequest;

/** Resolves the target path associated with a Spring AI request. */
@FunctionalInterface
public interface AgentsMdTargetPathResolver {

	/**
	 * Resolve a request target.
	 * @param request current request
	 * @return target file or directory
	 */
	Path resolve(ChatClientRequest request);

}
