package io.github.springaicommunity.agentsmd.codingagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RepositoryWorkspaceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void confinesPathsToTheWorkspace() throws IOException {
		Files.writeString(this.temporaryDirectory.resolve("Example.java"), "class Example {}");
		RepositoryWorkspace workspace = workspace();

		assertThat(workspace.resolve("Example.java", false)).isEqualTo(workspace.root().resolve("Example.java"));
		assertThatIllegalArgumentException().isThrownBy(() -> workspace.resolve("../outside", false))
			.withMessageContaining("escapes");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> workspace.resolve(this.temporaryDirectory.resolve("Example.java").toString(), false))
			.withMessageContaining("relative");
	}

	@Test
	void protectsRepositoryMetadataAndBuildOutput() throws IOException {
		Files.createDirectories(this.temporaryDirectory.resolve(".git"));
		Files.createDirectories(this.temporaryDirectory.resolve("module/target"));
		RepositoryWorkspace workspace = workspace();

		assertThatIllegalArgumentException().isThrownBy(() -> workspace.resolve(".git/config", true))
			.withMessageContaining("protected");
		assertThatIllegalArgumentException().isThrownBy(() -> workspace.resolve("module/target/result.txt", true))
			.withMessageContaining("protected");
	}

	@Test
	void rejectsSymbolicLinks() throws IOException {
		Path file = Files.writeString(this.temporaryDirectory.resolve("real.txt"), "content");
		Files.createSymbolicLink(this.temporaryDirectory.resolve("linked.txt"), file);

		assertThatIllegalArgumentException().isThrownBy(() -> workspace().resolve("linked.txt", false))
			.withMessageContaining("Symbolic links");
	}

	private RepositoryWorkspace workspace() {
		return new RepositoryWorkspace(
				new RepositoryStewardProperties(this.temporaryDirectory, DataSize.ofKilobytes(128), 200, 200, 50, 12));
	}

}
