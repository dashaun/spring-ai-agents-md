package io.github.springaicommunity.agentsmd.codingagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdActivePath;
import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdAdvisorParams;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryToolsTests {

	@TempDir
	Path temporaryDirectory;

	private RepositoryWorkspace workspace;

	private ChangeProposalStore proposals;

	private RepositoryTools tools;

	private AgentsMdActivePath activePath;

	private ToolContext toolContext;

	@BeforeEach
	void setUp() {
		var properties = new RepositoryStewardProperties(this.temporaryDirectory, DataSize.ofKilobytes(128), 3, 10, 5,
				4);
		this.workspace = new RepositoryWorkspace(properties);
		this.proposals = new ChangeProposalStore(this.workspace);
		this.tools = new RepositoryTools(this.workspace, this.proposals);
		this.activePath = new AgentsMdActivePath();
		this.toolContext = new ToolContext(Map.of(AgentsMdAdvisorParams.ACTIVE_PATH, this.activePath));
	}

	@Test
	void listsReadsAndSearchesBoundedRepositoryText() throws IOException {
		Files.createDirectory(this.workspace.root().resolve("src"));
		Files.writeString(this.workspace.root().resolve("src/Example.java"),
				"class Example {\n  String greeting = \"hello\";\n}\nlast line\n");

		assertThat(this.tools.listFiles("src", this.toolContext)).contains("file      src/Example.java");
		assertThat(this.tools.readFile("src/Example.java", 1, 10, this.toolContext)).contains("1: class Example",
				"3: }", "continue at line 4");
		assertThat(this.tools.readFile("src/Example.java", null, null, this.toolContext)).contains("1: class Example",
				"3: }", "continue at line 4");
		assertThat(this.tools.searchFiles("greeting", ".", this.toolContext))
			.contains("src/Example.java:2: String greeting");
		assertThat(this.activePath.get()).contains(this.workspace.root());
	}

	@Test
	void createsAProposalWithoutWritingAndRequiresUniqueReplacementText() throws IOException {
		Path file = Files.writeString(this.workspace.root().resolve("README.md"), "alpha\nbeta\n");

		assertThat(this.tools.proposePatch("README.md", "beta", "gamma", this.toolContext))
			.contains("Created change-001", "No file was changed");
		assertThat(Files.readString(file)).isEqualTo("alpha\nbeta\n");
		assertThat(this.proposals.preview("change-001")).contains("- beta", "+ gamma");

		Files.writeString(file, "same\nsame\n");
		assertThat(this.tools.proposePatch("README.md", "same", "other", this.toolContext))
			.contains("appears more than once");
	}

	@Test
	void createsANewFileProposalWithoutWriting() {
		Path file = this.workspace.root().resolve("notes.md");

		assertThat(this.tools.proposePatch("notes.md", "", "# Notes\n", this.toolContext))
			.contains("Created change-001");
		assertThat(file).doesNotExist();
	}

}
