package org.springframework.ai.autoconfigure.agents.discovery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.autoconfigure.agents.parser.AgentsMdDocument;

import static org.assertj.core.api.Assertions.assertThat;

class AgentsMdContextFormatterTests {

	@Test
	void byteSizeMatchesSerializedContextForSingleDocument() {
		List<AgentsMdResource> resources = List
			.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root instructions")));

		assertThat(AgentsMdContextFormatter.byteSize(resources))
			.isEqualTo(new AgentsMdResolution(Path.of("."), resources).toSystemPromptContext()
				.getBytes(StandardCharsets.UTF_8).length);
	}

	@Test
	void byteSizeMatchesSerializedContextForMultipleDocuments() {
		List<AgentsMdResource> resources = List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root")),
				new AgentsMdResource("module/AGENTS.md", new AgentsMdDocument("# Module")));

		assertThat(AgentsMdContextFormatter.byteSize(resources))
			.isEqualTo(new AgentsMdResolution(Path.of("."), resources).toSystemPromptContext()
				.getBytes(StandardCharsets.UTF_8).length);
	}

	@Test
	void byteSizeIsZeroForEmptyResources() {
		assertThat(AgentsMdContextFormatter.byteSize(List.of())).isZero();
	}

	@Test
	void formatMatchesExpectedEnvelope() {
		List<AgentsMdResource> resources = List.of(new AgentsMdResource("AGENTS.md", new AgentsMdDocument("# Root")),
				new AgentsMdResource("module/AGENTS.md", new AgentsMdDocument("# Module")));

		String context = AgentsMdContextFormatter.format(resources);

		assertThat(context).startsWith("# AGENTS.md instructions")
			.contains("When document instructions conflict, the closest document takes precedence.")
			.containsSubsequence("# Root", "# Module");
	}

}
