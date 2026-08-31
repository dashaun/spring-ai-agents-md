package org.springframework.ai.autoconfigure.agents.discovery;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.autoconfigure.agents.parser.AgentsMdParser;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance tests for the hierarchical semantics in the proposed AGENTS.md v1.1
 * clarification.
 */
class AgentsMdHierarchyConformanceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void accumulatesAncestorsBroadestFirstWithClosestConflictPrecedence() throws Exception {
		Path repository = repository();
		Files.writeString(repository.resolve("AGENTS.md"), "Use the repository formatter.");
		Path module = Files.createDirectories(repository.resolve("module"));
		Files.writeString(module.resolve("AGENTS.md"), "Use the module formatter.");
		Path feature = Files.createDirectories(module.resolve("feature"));
		Files.writeString(feature.resolve("AGENTS.md"), "Use the feature formatter.");

		AgentsMdResolution resolution = resolver(repository).resolve(feature.resolve("Example.java"));

		assertThat(resolution.resources()).extracting(AgentsMdResource::location)
			.containsExactly("AGENTS.md", Path.of("module", "AGENTS.md").toString(),
					Path.of("module", "feature", "AGENTS.md").toString());
		assertThat(resolution.toSystemPromptContext())
			.contains("When document instructions conflict, the closest document takes precedence.")
			.containsSubsequence("Use the repository formatter.", "Use the module formatter.",
					"Use the feature formatter.");
	}

	@Test
	void limitsJurisdictionToAncestorsWithinTheRepository() throws Exception {
		Files.writeString(this.temporaryDirectory.resolve("AGENTS.md"), "Outside repository");
		Path repository = repository();
		Files.writeString(repository.resolve("AGENTS.md"), "Repository instructions");
		Path applicable = Files.createDirectories(repository.resolve("applicable"));
		Files.writeString(applicable.resolve("AGENTS.md"), "Applicable instructions");
		Path sibling = Files.createDirectories(repository.resolve("sibling"));
		Files.writeString(sibling.resolve("AGENTS.md"), "Sibling instructions");

		String context = resolver(repository).resolve(applicable.resolve("Example.java")).toSystemPromptContext();

		assertThat(context).contains("Repository instructions", "Applicable instructions")
			.doesNotContain("Outside repository", "Sibling instructions");
	}

	@Test
	void rejectsInstructionSymlinkThatEscapesRepositoryJurisdiction() throws Exception {
		Path repository = repository();
		Path outside = Files.writeString(this.temporaryDirectory.resolve("outside-agents.md"), "Outside instructions");
		Files.createSymbolicLink(repository.resolve("AGENTS.md"), outside);

		AgentsMdResolution resolution = resolver(repository).resolve(repository.resolve("Example.java"));

		assertThat(resolution.resources()).isEmpty();
		assertThat(resolution.toSystemPromptContext()).doesNotContain("Outside instructions");
	}

	@Test
	void rejectsTargetSymlinkThatEscapesRepositoryJurisdiction() throws Exception {
		Path repository = repository();
		Files.writeString(repository.resolve("AGENTS.md"), "Repository instructions");
		Path outside = Files.createDirectories(this.temporaryDirectory.resolve("outside"));
		Path linkedDirectory = Files.createSymbolicLink(repository.resolve("linked"), outside);

		AgentsMdResolution resolution = resolver(repository).resolve(linkedDirectory.resolve("Example.java"));

		assertThat(resolution.resources()).singleElement()
			.satisfies(resource -> assertThat(resource.location()).isEqualTo("classpath:sample-agents.md"));
		assertThat(resolution.toSystemPromptContext()).doesNotContain("Repository instructions");
	}

	private Path repository() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectories(repository.resolve(".git"));
		return repository;
	}

	private FilesystemAgentsMdResolver resolver(Path workingDirectory) {
		return new FilesystemAgentsMdResolver(new AgentsMdParser(), new DefaultResourceLoader(),
				"classpath:sample-agents.md", workingDirectory);
	}

}
