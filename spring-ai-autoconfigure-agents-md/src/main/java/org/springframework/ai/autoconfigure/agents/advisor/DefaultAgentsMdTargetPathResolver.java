package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.util.Assert;

/** Resolves explicit, tool-propagated, and working-directory targets in that order. */
public class DefaultAgentsMdTargetPathResolver implements AgentsMdTargetPathResolver {

	private final Path workingDirectory;

	public DefaultAgentsMdTargetPathResolver(Path workingDirectory) {
		Assert.notNull(workingDirectory, "Working directory must not be null");
		this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
	}

	@Override
	public Path resolve(ChatClientRequest request) {
		Assert.notNull(request, "ChatClientRequest must not be null");
		Object explicitTarget = request.context().get(AgentsMdAdvisorParams.TARGET_PATH);
		if (explicitTarget != null) {
			return toPath(explicitTarget);
		}
		Object activePath = request.context().get(AgentsMdAdvisorParams.ACTIVE_PATH);
		if (activePath instanceof AgentsMdActivePath state) {
			return state.get().orElse(this.workingDirectory);
		}
		return this.workingDirectory;
	}

	private Path toPath(Object value) {
		if (value instanceof Path path) {
			return path;
		}
		if (value instanceof String path) {
			return Path.of(path);
		}
		throw new IllegalArgumentException(AgentsMdAdvisorParams.TARGET_PATH + " must be a Path or String");
	}

}
