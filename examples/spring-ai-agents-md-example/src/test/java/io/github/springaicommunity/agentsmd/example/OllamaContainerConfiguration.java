package io.github.springaicommunity.agentsmd.example;

import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class OllamaContainerConfiguration {

	static final String IMAGE = "ghcr.io/dashaun/testcontainer-ollama-smollm2-135m:0.33.2";

	@Bean
	@ServiceConnection
	OllamaContainer ollamaContainer() {
		return createContainer();
	}

	static OllamaContainer createContainer() {
		return new OllamaContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("ollama/ollama"))
			.withStartupTimeout(Duration.ofMinutes(5));
	}

}
