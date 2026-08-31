package org.springframework.ai.autoconfigure.agents.advisor;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentsMdTargetPathResolverTests {

	private final Path workingDirectory = Path.of("workspace").toAbsolutePath().normalize();

	private final DefaultAgentsMdTargetPathResolver resolver = new DefaultAgentsMdTargetPathResolver(
			this.workingDirectory);

	@Test
	void explicitRequestTargetTakesPriority() {
		AgentsMdActivePath activePath = new AgentsMdActivePath();
		activePath.update(Path.of("tool-target"));
		ChatClientRequest request = request(Map.of(AgentsMdAdvisorParams.TARGET_PATH, Path.of("explicit-target"),
				AgentsMdAdvisorParams.ACTIVE_PATH, activePath));

		assertThat(this.resolver.resolve(request)).isEqualTo(Path.of("explicit-target"));
	}

	@Test
	void filesystemToolCanPropagateTheActivePath() {
		AgentsMdActivePath activePath = new AgentsMdActivePath();
		ToolContext toolContext = new ToolContext(Map.of(AgentsMdAdvisorParams.ACTIVE_PATH, activePath));

		AgentsMdAdvisorParams.propagateActivePath(toolContext, Path.of("module/src/Example.java"));

		assertThat(this.resolver.resolve(request(Map.of(AgentsMdAdvisorParams.ACTIVE_PATH, activePath))))
			.isEqualTo(Path.of("module/src/Example.java"));
	}

	@Test
	void workingDirectoryIsTheZeroConfigurationFallback() {
		assertThat(this.resolver.resolve(request(Map.of()))).isEqualTo(this.workingDirectory);
	}

	private ChatClientRequest request(Map<String, Object> context) {
		return new ChatClientRequest(new Prompt(new UserMessage("Hello")), context);
	}

}
