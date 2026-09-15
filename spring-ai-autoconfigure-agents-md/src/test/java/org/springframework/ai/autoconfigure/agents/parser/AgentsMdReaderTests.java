package org.springframework.ai.autoconfigure.agents.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentsMdReaderTests {

	private final AgentsMdReader reader = new AgentsMdReader();

	@Test
	void preservesArbitraryMarkdownFromResource() throws Exception {
		AgentsMdDocument document = this.reader.read(new ClassPathResource("sample-agents.md"));

		assertThat(document.content()).contains("# Sample AGENTS.md", "## Dev environment tips",
				"## Testing instructions", "## PR instructions");
		assertThat(document.toSystemPromptContext()).isEqualTo(document.content());
	}

	@Test
	void preservesMarkdownWithoutInterpretingHeadings() throws Exception {
		String markdown = """
				# Any heading is valid

				Use **standard Markdown**, tables, and project-specific sections.

				| Check | Command |
				| --- | --- |
				| Test | `./mvnw test` |
				""";

		AgentsMdDocument document = this.reader
			.read(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

		assertThat(document.content()).isEqualTo(markdown);
	}

	@Test
	void preservesLineEndingsAndWhitespace() {
		String markdown = "# Instructions\r\n\r\n  Keep indentation.  \r\n";

		assertThat(this.reader.read(markdown).content()).isEqualTo(markdown);
	}

	@Test
	void supportsAnEmptyDocumentAndRejectsNull() {
		assertThat(this.reader.read("").content()).isEmpty();
		assertThatThrownBy(() -> this.reader.read((String) null)).isInstanceOf(IllegalArgumentException.class);
	}

}
