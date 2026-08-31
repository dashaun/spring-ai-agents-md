package org.springframework.ai.autoconfigure.agents.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentsMdParserTests {

	private final AgentsMdParser parser = new AgentsMdParser();

	@Test
	void preservesArbitraryMarkdownFromResource() throws Exception {
		AgentsMdDocument document = this.parser.parse(new ClassPathResource("sample-agents.md"));

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

		AgentsMdDocument document = this.parser
			.parse(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

		assertThat(document.content()).isEqualTo(markdown);
	}

	@Test
	void preservesLineEndingsAndWhitespace() {
		String markdown = "# Instructions\r\n\r\n  Keep indentation.  \r\n";

		assertThat(this.parser.parse(markdown).content()).isEqualTo(markdown);
	}

	@Test
	void supportsAnEmptyDocumentAndRejectsNull() {
		assertThat(this.parser.parse("").content()).isEmpty();
		assertThatThrownBy(() -> this.parser.parse((String) null)).isInstanceOf(IllegalArgumentException.class);
	}

}
