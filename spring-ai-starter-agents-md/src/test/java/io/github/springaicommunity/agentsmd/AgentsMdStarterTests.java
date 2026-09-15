package io.github.springaicommunity.agentsmd;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdSystemAdvisor;
import org.springframework.ai.autoconfigure.agents.config.AgentsMdProperties;
import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.autoconfigure.agents.parser.AgentsMdReader;
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
	private AgentsMdReader reader;

	@Autowired
	private AgentsMdResolver resolver;

	@Autowired
	private AgentsMdSystemAdvisor advisor;

	@Autowired
	private AgentsMdProperties properties;

	@TempDir
	Path temporaryDirectory;

	@Test
	void starterActivatesAutoConfiguration() {
		assertThat(this.reader).isNotNull();
		assertThat(this.resolver).isNotNull();
		assertThat(this.advisor).isNotNull();
		assertThat(this.properties.isEnabled()).isTrue();
	}

	@Test
	void debugLoggingReportsMetadataWithoutDocumentContents(CapturedOutput output) throws Exception {
		Files.createDirectory(this.temporaryDirectory.resolve(".git"));
		Files.writeString(this.temporaryDirectory.resolve("AGENTS.md"),
				"# Instructions\n\nSENSITIVE_INSTRUCTION_MUST_NOT_BE_LOGGED\n");

		this.resolver.resolve(this.temporaryDirectory.resolve("Example.java"));

		assertThat(output).contains("Loaded applicable AGENTS.md")
			.doesNotContain("SENSITIVE_INSTRUCTION_MUST_NOT_BE_LOGGED");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestApplication {

	}

}
