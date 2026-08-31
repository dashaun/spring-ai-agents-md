package io.github.springaicommunity.agentsmd.example;

import org.springframework.boot.SpringApplication;

public class TestAgentsMdExampleApplication {

	public static void main(String[] args) {
		SpringApplication.from(AgentsMdExampleApplication::main).with(OllamaContainerConfiguration.class).run(args);
	}

}
