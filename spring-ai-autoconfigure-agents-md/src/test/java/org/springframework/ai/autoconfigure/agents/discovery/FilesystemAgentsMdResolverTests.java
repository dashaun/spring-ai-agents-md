package org.springframework.ai.autoconfigure.agents.discovery;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.autoconfigure.agents.parser.AgentsMdParser;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemAgentsMdResolverTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void resolvesApplicableDocumentsFromBroadestToClosest() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Repository instructions");
		Path module = Files.createDirectories(repository.resolve("module/src/main/java"));
		Files.writeString(repository.resolve("module/AGENTS.md"), "# Module instructions");
		Path target = module.resolve("Example.java");

		AgentsMdResolution resolution = resolver(repository).resolve(target);

		assertThat(resolution.target()).isEqualTo(target.toAbsolutePath().normalize());
		assertThat(resolution.resources()).extracting(resource -> resource.document().content())
			.containsExactly("# Repository instructions", "# Module instructions");
		assertThat(resolution.resources()).extracting(AgentsMdResource::location)
			.containsExactly("AGENTS.md", Path.of("module", "AGENTS.md").toString());
		assertThat(resolution.toSystemPromptContext()).containsSubsequence("# Repository instructions",
				"# Module instructions");
	}

	@Test
	void composesDeepHierarchyAndStatesClosestConflictPrecedence() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "Use the repository formatter.");
		Path module = Files.createDirectories(repository.resolve("module"));
		Files.writeString(module.resolve("AGENTS.md"), "Use the module formatter.");
		Path feature = Files.createDirectories(module.resolve("feature"));
		Files.writeString(feature.resolve("AGENTS.md"), "Use the feature formatter.");

		String context = resolver(repository).resolve(feature.resolve("Example.java")).toSystemPromptContext();

		assertThat(context).contains("When document instructions conflict, the closest document takes precedence.")
			.containsSubsequence("Use the repository formatter.", "Use the module formatter.",
					"Use the feature formatter.");
	}

	@Test
	void doesNotApplyInstructionsFromSiblingDirectories() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Repository instructions");
		Path first = Files.createDirectories(repository.resolve("first"));
		Files.writeString(first.resolve("AGENTS.md"), "# First instructions");
		Path second = Files.createDirectories(repository.resolve("second"));
		Files.writeString(second.resolve("AGENTS.md"), "# Second instructions");

		String context = resolver(repository).resolve(first.resolve("Example.java")).toSystemPromptContext();

		assertThat(context).contains("# Repository instructions", "# First instructions")
			.doesNotContain("# Second instructions");
	}

	@Test
	void doesNotTraversePastRepositoryBoundary() throws Exception {
		Files.writeString(this.temporaryDirectory.resolve("AGENTS.md"), "# Outside instructions");
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Repository instructions");
		Path target = Files.createDirectories(repository.resolve("module")).resolve("Example.java");

		String context = resolver(repository).resolve(target).toSystemPromptContext();

		assertThat(context).contains("# Repository instructions").doesNotContain("# Outside instructions");
	}

	@Test
	void doesNotDiscoverFilesystemInstructionsOutsideWorkingDirectoryWithoutRepository() throws Exception {
		Path workspace = Files.createDirectories(this.temporaryDirectory.resolve("workspace"));
		Path outside = Files.createDirectories(this.temporaryDirectory.resolve("outside"));
		Files.writeString(outside.resolve("AGENTS.md"), "# Outside instructions");

		AgentsMdResolution resolution = resolver(workspace).resolve(outside.resolve("Example.java"));

		assertThat(resolution.resources()).singleElement()
			.satisfies(resource -> assertThat(resource.location()).isEqualTo("classpath:sample-agents.md"));
		assertThat(resolution.toSystemPromptContext()).doesNotContain("# Outside instructions");
	}

	@Test
	void filesystemInstructionsTakePriorityOverClasspathFallback() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Filesystem instructions");

		AgentsMdResolution resolution = resolver(repository).resolve(repository);

		assertThat(resolution.resources()).singleElement()
			.satisfies(resource -> assertThat(resource.document().content()).isEqualTo("# Filesystem instructions"));
	}

	@Test
	void usesClasspathFallbackWhenNoFilesystemDocumentApplies() {
		AgentsMdResolution resolution = resolver(this.temporaryDirectory).resolve(this.temporaryDirectory);

		assertThat(resolution.resources()).singleElement()
			.satisfies(resource -> assertThat(resource.location()).isEqualTo("classpath:sample-agents.md"));
	}

	@Test
	void resolvesRelativeAndNotYetCreatedTargetsAgainstWorkingDirectory() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Create files here");
		Files.createDirectories(repository.resolve("new/package"));

		AgentsMdResolution resolution = resolver(repository).resolve(Path.of("new/package/NewFile.java"));

		assertThat(resolution.target())
			.isEqualTo(repository.resolve("new/package/NewFile.java").toAbsolutePath().normalize());
		assertThat(resolution.toSystemPromptContext()).contains("# Create files here");
	}

	@Test
	void explicitLocationReplacesFilesystemDiscovery() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Filesystem instructions");
		FilesystemAgentsMdResolver resolver = new FilesystemAgentsMdResolver(new AgentsMdParser(),
				new DefaultResourceLoader(), "classpath:sample-agents.md", "classpath:AGENTS.md", repository);

		AgentsMdResolution resolution = resolver.resolve(repository);

		assertThat(resolution.resources()).singleElement()
			.satisfies(resource -> assertThat(resource.location()).isEqualTo("classpath:sample-agents.md"));
		assertThat(resolution.toSystemPromptContext()).doesNotContain("# Filesystem instructions");
	}

	@Test
	void explicitlyConfiguredMissingResourceFailsClearly() {
		FilesystemAgentsMdResolver resolver = new FilesystemAgentsMdResolver(new AgentsMdParser(),
				new DefaultResourceLoader(), "classpath:missing-agents.md", "classpath:AGENTS.md",
				this.temporaryDirectory);

		assertThatThrownBy(() -> resolver.resolve(this.temporaryDirectory)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Configured AGENTS.md does not exist");
	}

	@Test
	void reloadsFilesystemInstructionsOnEveryResolution() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Path instructions = repository.resolve("AGENTS.md");
		Files.writeString(instructions, "# First version");
		FilesystemAgentsMdResolver resolver = resolver(repository);

		assertThat(resolver.resolve(repository).toSystemPromptContext()).contains("# First version");
		Files.writeString(instructions, "# Reloaded version");

		assertThat(resolver.resolve(repository).toSystemPromptContext()).contains("# Reloaded version")
			.doesNotContain("# First version");
	}

	@Test
	void rejectsAnOversizedDocumentWithoutUsingTheFallback() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "123456789");

		AgentsMdResolution resolution = resolver(repository, 32, 16, 8, 256).resolve(repository);

		assertThat(resolution.resources()).isEmpty();
		assertThat(resolution.outcome()).isEqualTo(AgentsMdResolutionOutcome.SIZE_LIMIT);
		assertThat(resolution.toSystemPromptContext()).doesNotContain("Sample AGENTS.md");
	}

	@Test
	void stopsBeforeExceedingTheAggregateSizeLimit() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "123456");
		Path module = Files.createDirectories(repository.resolve("module"));
		Files.writeString(module.resolve("AGENTS.md"), "abcdef");

		AgentsMdResolution resolution = resolver(repository, 32, 16, 64, 300).resolve(module);

		assertThat(resolution.resources()).extracting(resource -> resource.document().content())
			.containsExactly("123456");
		assertThat(resolution.outcome()).isEqualTo(AgentsMdResolutionOutcome.SIZE_LIMIT);
	}

	@Test
	void stopsAtTheDocumentLimit() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Root");
		Path module = Files.createDirectories(repository.resolve("module"));
		Files.writeString(module.resolve("AGENTS.md"), "# Module");

		AgentsMdResolution resolution = resolver(repository, 32, 1, 64, 256).resolve(module);

		assertThat(resolution.resources()).extracting(AgentsMdResource::location).containsExactly("AGENTS.md");
		assertThat(resolution.outcome()).isEqualTo(AgentsMdResolutionOutcome.DOCUMENT_LIMIT);
	}

	@Test
	void stopsAtTheTraversalDepthLimit() throws Exception {
		Path repository = Files.createDirectories(this.temporaryDirectory.resolve("repository"));
		Files.createDirectory(repository.resolve(".git"));
		Files.writeString(repository.resolve("AGENTS.md"), "# Root");
		Path module = Files.createDirectories(repository.resolve("module"));
		Files.writeString(module.resolve("AGENTS.md"), "# Module");

		AgentsMdResolution resolution = resolver(repository, 1, 16, 64, 256).resolve(module);

		assertThat(resolution.resources()).extracting(AgentsMdResource::location)
			.containsExactly(Path.of("module", "AGENTS.md").toString());
		assertThat(resolution.outcome()).isEqualTo(AgentsMdResolutionOutcome.DEPTH_LIMIT);
	}

	private FilesystemAgentsMdResolver resolver(Path workingDirectory) {
		return new FilesystemAgentsMdResolver(new AgentsMdParser(), new DefaultResourceLoader(),
				"classpath:sample-agents.md", workingDirectory);
	}

	private FilesystemAgentsMdResolver resolver(Path workingDirectory, int maxDepth, int maxDocuments,
			long maxDocumentSize, long maxTotalSize) {
		return new FilesystemAgentsMdResolver(new AgentsMdParser(), new DefaultResourceLoader(), null,
				"classpath:sample-agents.md", workingDirectory, maxDepth, maxDocuments, maxDocumentSize, maxTotalSize);
	}

}
