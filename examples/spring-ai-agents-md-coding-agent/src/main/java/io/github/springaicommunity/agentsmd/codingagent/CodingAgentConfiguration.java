package io.github.springaicommunity.agentsmd.codingagent;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

@Configuration(proxyBeanMethods = false)
class CodingAgentConfiguration {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.defaultSystem("""
				You are a careful repository steward. Inspect repository files with the available tools before
				making claims about the code. Follow every applicable AGENTS.md instruction. When the user
				requests a repository change, you MUST complete the request by invoking proposePatch; a textual
				suggestion is not a completed change request. proposePatch creates a reviewable proposal and
				never edits a file. Tell the user the proposal ID and ask them to review it with show-change and
				approve it with apply-change. Never claim a proposal was applied, a command was run, or a test
				passed unless a tool result proves it.
				""").build();
	}

	@Bean
	RepositoryWorkspace repositoryWorkspace(RepositoryStewardProperties properties) {
		return new RepositoryWorkspace(properties);
	}

	@Bean
	ToolCallbackProvider repositoryToolCallbacks(RepositoryTools tools) {
		return MethodToolCallbackProvider.builder().toolObjects(tools).build();
	}

	@Bean
	PromptProvider repositoryStewardPrompt() {
		return () -> new AttributedString("steward:> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
	}

}
