package io.github.springaicommunity.agentsmd.codingagent;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(args = "help", properties = "spring.shell.interactive.enabled=false")
class RepositoryStewardCommandsTests {

	@Autowired
	private RepositoryStewardCommands commands;

	@Autowired
	private ToolCallbackProvider toolCallbacks;

	@MockitoBean
	private ChatModel chatModel;

	@Test
	void asksAnAgentsMdAwareChatClient() {
		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		when(this.chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Ready to steward.")))));

		assertThat(this.commands.steward("What instructions apply?")).isEqualTo("Ready to steward.");

		var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
		verify(this.chatModel).call(prompt.capture());
		assertThat(prompt.getValue().getSystemMessage().getText()).contains("You are a repository steward.")
			.contains("File changes are proposals until the user explicitly runs `apply-change`");
		assertThat(this.toolCallbacks.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
			.containsExactlyInAnyOrder("listFiles", "readFile", "searchFiles", "proposePatch");
	}

	@Test
	void rejectsFabricatedProposalIdsWhenNoToolCreatedAProposal() {
		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		when(this.chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Created fake-id.")))));

		assertThat(this.commands.steward("Propose a README change"))
			.contains("No proposal was created", "no proposal ID is valid")
			.doesNotContain("fake-id");
	}

}
