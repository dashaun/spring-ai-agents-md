package io.github.springaicommunity.agentsmd.example;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.wait.strategy.Wait.forHttp;

@SpringBootTest
@Testcontainers
class WireMockOllamaIT {

	private static final int WIREMOCK_PORT = 8080;

	@Container
	static final GenericContainer<?> WIREMOCK = new GenericContainer<>(
			DockerImageName.parse("wiremock/wiremock:3.13.1"))
		.withCopyFileToContainer(MountableFile.forClasspathResource("wiremock"), "/home/wiremock/mappings")
		.withExposedPorts(WIREMOCK_PORT)
		.waitingFor(forHttp("/__admin/health").forPort(WIREMOCK_PORT).forStatusCode(200));

	@Autowired
	private ChatClient chatClient;

	@DynamicPropertySource
	static void configureOllama(DynamicPropertyRegistry registry) {
		registry.add("spring.ai.ollama.base-url",
				() -> "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(WIREMOCK_PORT));
		registry.add("spring.ai.ollama.chat.options.model", () -> "mock-agent");
	}

	@Test
	void sendsAgentsMdAsAnOllamaSystemMessage() {
		String response = this.chatClient.prompt().user("Confirm that you are ready.").call().content();

		assertThat(response).isEqualTo("AGENTS.md active: deterministic response");
	}

}
