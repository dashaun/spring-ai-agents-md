package io.github.springaicommunity.agentsmd.example;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentsMdExampleApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatModel chatModel;

	@Test
	void addsAgentsMdToSystemPromptAndPublishesAdvisorMetric() throws Exception {
		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		when(this.chatModel.call(any(Prompt.class))).thenReturn(
				new ChatResponse(List.of(new Generation(new AssistantMessage("AGENTS.md active: mocked response")))));

		this.mockMvc
			.perform(post("/chat").contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"What instructions are active?\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("AGENTS.md active: mocked response"));

		var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
		verify(this.chatModel).call(prompt.capture());
		assertThat(prompt.getValue().getSystemMessage().getText())
			.contains("Begin every answer with the exact text `AGENTS.md active:`.");

		this.mockMvc.perform(get("/actuator/metrics/spring.ai.agents.md.advisor"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("spring.ai.agents.md.advisor"));
	}

}
