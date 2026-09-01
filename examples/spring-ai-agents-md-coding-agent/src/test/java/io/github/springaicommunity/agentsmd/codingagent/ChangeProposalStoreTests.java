package io.github.springaicommunity.agentsmd.codingagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ChangeProposalStoreTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void previewsAndAppliesAnExplicitlyApprovedProposal() throws IOException {
		RepositoryWorkspace workspace = workspace();
		Path file = Files.writeString(workspace.root().resolve("Greeting.java"), "class Greeting {}\n");
		ChangeProposalStore store = new ChangeProposalStore(workspace);
		ChangeProposal proposal = store.propose(file, "class Greeting {}\n", "final class Greeting {}\n", false);

		assertThat(store.list()).contains(proposal.id(), "modify Greeting.java");
		assertThat(store.preview(proposal.id())).contains("--- a/Greeting.java", "- class Greeting {}",
				"+ final class Greeting {}");
		assertThat(Files.readString(file)).isEqualTo("class Greeting {}\n");

		assertThat(store.apply(proposal.id())).contains("Applied", "Greeting.java");
		assertThat(Files.readString(file)).isEqualTo("final class Greeting {}\n");
		assertThat(store.list()).isEqualTo("No pending changes.");
	}

	@Test
	void refusesToOverwriteAFileChangedAfterProposal() throws IOException {
		RepositoryWorkspace workspace = workspace();
		Path file = Files.writeString(workspace.root().resolve("README.md"), "before\n");
		ChangeProposalStore store = new ChangeProposalStore(workspace);
		ChangeProposal proposal = store.propose(file, "before\n", "after\n", false);
		Files.writeString(file, "changed elsewhere\n");

		assertThatIllegalStateException().isThrownBy(() -> store.apply(proposal.id()))
			.withMessageContaining("changed after");
		assertThat(Files.readString(file)).isEqualTo("changed elsewhere\n");
	}

	@Test
	void previewsOnlyTheChangedLinesAndNearbyContext() throws IOException {
		RepositoryWorkspace workspace = workspace();
		String before = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\n";
		String after = "one\ntwo\nthree\nfour\nchanged\nsix\nseven\neight\nnine\n";
		Path file = Files.writeString(workspace.root().resolve("README.md"), before);
		ChangeProposalStore store = new ChangeProposalStore(workspace);
		ChangeProposal proposal = store.propose(file, before, after, false);

		assertThat(store.preview(proposal.id())).contains("@@ -2,7 +2,7 @@", "  four", "- five", "+ changed", "  eight")
			.doesNotContain("  one", "  nine");
	}

	@Test
	void discardsWithoutWritingAndCanCreateAFile() throws IOException {
		RepositoryWorkspace workspace = workspace();
		ChangeProposalStore store = new ChangeProposalStore(workspace);
		Path discardedFile = workspace.root().resolve("discarded.txt");
		ChangeProposal discarded = store.propose(discardedFile, "", "do not write", true);

		assertThat(store.discard(discarded.id())).contains("no files were changed");
		assertThat(discardedFile).doesNotExist();

		Path createdFile = workspace.root().resolve("created.txt");
		ChangeProposal created = store.propose(createdFile, "", "created\n", true);
		store.apply(created.id());
		assertThat(createdFile).hasContent("created\n");
	}

	private RepositoryWorkspace workspace() {
		var properties = new RepositoryStewardProperties(this.temporaryDirectory, DataSize.ofKilobytes(128), 200, 200,
				50, 12);
		return new RepositoryWorkspace(properties);
	}

}
