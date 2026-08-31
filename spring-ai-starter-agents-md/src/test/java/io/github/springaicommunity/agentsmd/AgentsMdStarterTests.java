package io.github.springaicommunity.agentsmd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdSystemAdvisor;
import org.springframework.ai.autoconfigure.agents.config.AgentsMdProperties;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.parser.AgentsMdParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "logging.level.org.springframework.ai.autoconfigure.agents=DEBUG")
@ExtendWith(OutputCaptureExtension.class)
class AgentsMdStarterTests {

	@Autowired
	private AgentsMdParser parser;

	@Autowired
	private AgentsMdResolver resolver;

	@Autowired
	private AgentsMdSystemAdvisor advisor;

	@Autowired
	private AgentsMdProperties properties;

	@Test
	void starterActivatesAutoConfiguration() {
		assertThat(this.parser).isNotNull();
		assertThat(this.resolver).isNotNull();
		assertThat(this.advisor).isNotNull();
		assertThat(this.properties.isEnabled()).isTrue();
	}

	@Test
	void debugLoggingReportsMetadataWithoutDocumentContents(CapturedOutput output) {
		this.resolver.resolve(java.nio.file.Path.of(System.getProperty("user.dir")));

		assertThat(output).contains("Loaded applicable AGENTS.md")
			.doesNotContain("SENSITIVE_INSTRUCTION_MUST_NOT_BE_LOGGED");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

	}

}
