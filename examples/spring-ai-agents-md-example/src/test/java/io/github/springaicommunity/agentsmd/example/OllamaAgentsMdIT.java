package io.github.springaicommunity.agentsmd.example;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OllamaAgentsMdIT {

	@Container
	@ServiceConnection
	static final OllamaContainer OLLAMA = OllamaContainerConfiguration.createContainer();

	@Autowired
	private ChatClient chatClient;

	@Test
	@Timeout(value = 5, unit = TimeUnit.MINUTES)
	void ollamaReturnsAResponse() {
		String response = this.chatClient.prompt().user("Confirm that you are ready.").call().content();

		assertThat(response).isNotBlank();
	}

}
