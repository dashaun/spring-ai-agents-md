package org.springframework.ai.autoconfigure.agents.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.autoconfigure.agents.parser.AgentsMdParser;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

/**
 * Resolves filesystem {@code AGENTS.md} files relative to a target, with a Spring
 * {@link Resource} fallback when no filesystem document applies.
 */
public class FilesystemAgentsMdResolver implements AgentsMdResolver {

	private static final Logger logger = LoggerFactory.getLogger(FilesystemAgentsMdResolver.class);

	private static final String FILE_NAME = "AGENTS.md";

	private static final int DEFAULT_MAX_DEPTH = 32;

	private static final int DEFAULT_MAX_DOCUMENTS = 16;

	private static final long DEFAULT_MAX_DOCUMENT_SIZE = 64 * 1024;

	private static final long DEFAULT_MAX_TOTAL_SIZE = 256 * 1024;

	private final AgentsMdParser parser;

	private final ResourceLoader resourceLoader;

	private final @Nullable String explicitLocation;

	private final String fallbackLocation;

	private final Path workingDirectory;

	private final int maxDepth;

	private final int maxDocuments;

	private final long maxDocumentSize;

	private final long maxTotalSize;

	public FilesystemAgentsMdResolver(AgentsMdParser parser, ResourceLoader resourceLoader, String fallbackLocation,
			Path workingDirectory) {
		this(parser, resourceLoader, null, fallbackLocation, workingDirectory, DEFAULT_MAX_DEPTH, DEFAULT_MAX_DOCUMENTS,
				DEFAULT_MAX_DOCUMENT_SIZE, DEFAULT_MAX_TOTAL_SIZE);
	}

	public FilesystemAgentsMdResolver(AgentsMdParser parser, ResourceLoader resourceLoader,
			@Nullable String explicitLocation, String fallbackLocation, Path workingDirectory) {
		this(parser, resourceLoader, explicitLocation, fallbackLocation, workingDirectory, DEFAULT_MAX_DEPTH,
				DEFAULT_MAX_DOCUMENTS, DEFAULT_MAX_DOCUMENT_SIZE, DEFAULT_MAX_TOTAL_SIZE);
	}

	public FilesystemAgentsMdResolver(AgentsMdParser parser, ResourceLoader resourceLoader,
			@Nullable String explicitLocation, String fallbackLocation, Path workingDirectory, int maxDepth,
			int maxDocuments, long maxDocumentSize, long maxTotalSize) {
		Assert.notNull(parser, "AgentsMdParser must not be null");
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		if (explicitLocation != null) {
			Assert.hasText(explicitLocation, "Explicit location must not be empty");
		}
		Assert.hasText(fallbackLocation, "Fallback location must not be empty");
		Assert.notNull(workingDirectory, "Working directory must not be null");
		Assert.isTrue(maxDepth > 0, "Max depth must be greater than zero");
		Assert.isTrue(maxDocuments > 0, "Max documents must be greater than zero");
		Assert.isTrue(maxDocumentSize > 0 && maxDocumentSize < Integer.MAX_VALUE,
				"Max document size must be between 1 byte and 2 GB");
		Assert.isTrue(maxTotalSize > 0 && maxTotalSize < Integer.MAX_VALUE,
				"Max total size must be between 1 byte and 2 GB");
		this.parser = parser;
		this.resourceLoader = resourceLoader;
		this.explicitLocation = explicitLocation;
		this.fallbackLocation = fallbackLocation;
		this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
		this.maxDepth = maxDepth;
		this.maxDocuments = maxDocuments;
		this.maxDocumentSize = maxDocumentSize;
		this.maxTotalSize = maxTotalSize;
	}

	@Override
	public AgentsMdResolution resolve(Path target) {
		Assert.notNull(target, "Target must not be null");
		Path normalizedTarget = normalize(target);
		if (this.explicitLocation != null) {
			LoadedResource configured = requiredResource(this.explicitLocation);
			return resolution(normalizedTarget, configured);
		}
		FilesystemResult filesystem = filesystemResources(normalizedTarget);
		if (!filesystem.applicableDocumentFound()) {
			LoadedResource fallback = fallbackResource();
			if (fallback.resource() != null || fallback.sizeLimit()) {
				return resolution(normalizedTarget, fallback);
			}
		}
		return new AgentsMdResolution(normalizedTarget, filesystem.resources(), filesystem.outcome(),
				filesystem.configuredLimit());
	}

	private AgentsMdResolution resolution(Path target, LoadedResource loaded) {
		if (loaded.sizeLimit() || loaded.resource() == null
				|| contextSize(List.of(loaded.resource())) > this.maxTotalSize) {
			if (loaded.resource() != null) {
				logger.warn("Ignoring AGENTS.md at {} because it exceeds the {} byte aggregate limit",
						loaded.resource().location(), this.maxTotalSize);
			}
			long configuredLimit = loaded.sizeLimit() ? this.maxDocumentSize : this.maxTotalSize;
			return new AgentsMdResolution(target, List.of(), AgentsMdResolutionOutcome.SIZE_LIMIT, configuredLimit);
		}
		return new AgentsMdResolution(target, List.of(loaded.resource()), AgentsMdResolutionOutcome.COMPLETE);
	}

	private LoadedResource requiredResource(String location) {
		Resource resource = this.resourceLoader.getResource(location);
		if (!resource.exists()) {
			throw new IllegalStateException("Configured AGENTS.md does not exist at " + location);
		}
		try {
			return readResource(resource, location, "explicitly configured");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read configured AGENTS.md at " + location, ex);
		}
	}

	private LoadedResource fallbackResource() {
		Resource resource = this.resourceLoader.getResource(this.fallbackLocation);
		if (!resource.exists()) {
			logger.debug("No AGENTS.md filesystem resource or fallback found at {}", this.fallbackLocation);
			return LoadedResource.missing();
		}
		try {
			return readResource(resource, this.fallbackLocation, "fallback");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read AGENTS.md fallback at " + this.fallbackLocation, ex);
		}
	}

	private FilesystemResult filesystemResources(Path target) {
		Path directory = Files.isDirectory(target) ? target : target.getParent();
		if (directory == null) {
			return FilesystemResult.empty();
		}
		Path boundary = discoveryBoundary(directory);
		if (boundary == null) {
			logger.debug("Target {} is outside the AGENTS.md discovery boundary {}", target, this.workingDirectory);
			return FilesystemResult.empty();
		}
		if (!isWithinBoundary(directory, boundary)) {
			logger.warn("Ignoring AGENTS.md discovery for target outside its resolved boundary: {}", target);
			return FilesystemResult.empty();
		}
		List<Path> candidates = new ArrayList<>();
		AgentsMdResolutionOutcome outcome = AgentsMdResolutionOutcome.COMPLETE;
		long configuredLimit = 0;
		int depth = 0;
		for (Path current = directory; current != null && current.startsWith(boundary); current = current.getParent()) {
			if (depth >= this.maxDepth) {
				logger.warn("Stopped AGENTS.md discovery at the configured depth limit of {}", this.maxDepth);
				outcome = AgentsMdResolutionOutcome.DEPTH_LIMIT;
				configuredLimit = this.maxDepth;
				break;
			}
			Path candidate = current.resolve(FILE_NAME);
			if (Files.isRegularFile(candidate)) {
				candidates.add(candidate);
			}
			if (current.equals(boundary)) {
				break;
			}
			depth++;
		}
		Collections.reverse(candidates);
		List<AgentsMdResource> resources = new ArrayList<>();
		for (Path candidate : candidates) {
			if (resources.size() >= this.maxDocuments) {
				logger.warn("Stopped AGENTS.md composition at the configured document limit of {}", this.maxDocuments);
				outcome = AgentsMdResolutionOutcome.DOCUMENT_LIMIT;
				configuredLimit = this.maxDocuments;
				break;
			}
			try {
				if (!candidate.toRealPath().startsWith(boundary.toRealPath())) {
					logger.warn("Ignoring AGENTS.md that resolves outside the project boundary: {}", candidate);
					continue;
				}
				LoadedResource loaded = readResource(this.resourceLoader.getResource(candidate.toUri().toString()),
						boundary.relativize(candidate).toString(), "applicable");
				if (loaded.resource() == null) {
					outcome = AgentsMdResolutionOutcome.SIZE_LIMIT;
					configuredLimit = this.maxDocumentSize;
					continue;
				}
				if (contextSizeWith(resources, loaded.resource()) > this.maxTotalSize) {
					logger.warn("Stopped AGENTS.md composition before exceeding the {} byte aggregate limit",
							this.maxTotalSize);
					outcome = AgentsMdResolutionOutcome.SIZE_LIMIT;
					configuredLimit = this.maxTotalSize;
					break;
				}
				resources.add(loaded.resource());
			}
			catch (IOException ex) {
				logger.warn("Unable to read applicable AGENTS.md at {}", candidate, ex);
			}
		}
		return new FilesystemResult(resources, outcome, configuredLimit,
				!candidates.isEmpty() || outcome != AgentsMdResolutionOutcome.COMPLETE);
	}

	private long contextSizeWith(List<AgentsMdResource> resources, AgentsMdResource candidate) {
		List<AgentsMdResource> proposed = new ArrayList<>(resources);
		proposed.add(candidate);
		return contextSize(proposed);
	}

	private long contextSize(List<AgentsMdResource> resources) {
		return new AgentsMdResolution(this.workingDirectory, resources).toSystemPromptContext()
			.getBytes(StandardCharsets.UTF_8).length;
	}

	private LoadedResource readResource(Resource resource, String location, String description) throws IOException {
		byte[] bytes;
		try (InputStream inputStream = resource.getInputStream()) {
			bytes = inputStream.readNBytes((int) this.maxDocumentSize + 1);
		}
		if (bytes.length > this.maxDocumentSize) {
			logger.warn("Ignoring {} AGENTS.md at {} because it exceeds the {} byte document limit", description,
					location, this.maxDocumentSize);
			return LoadedResource.sizeExceeded();
		}
		AgentsMdResource loaded = new AgentsMdResource(location,
				this.parser.parse(new String(bytes, StandardCharsets.UTF_8)));
		logger.debug("Loaded {} AGENTS.md from {} ({} bytes, {} characters)", description, location, bytes.length,
				loaded.document().content().length());
		return new LoadedResource(loaded, false);
	}

	private boolean isWithinBoundary(Path directory, Path boundary) {
		Path existing = directory;
		while (existing != null && !Files.exists(existing)) {
			existing = existing.getParent();
		}
		if (existing == null) {
			return false;
		}
		try {
			return existing.toRealPath().startsWith(boundary.toRealPath());
		}
		catch (IOException ex) {
			logger.warn("Unable to verify AGENTS.md discovery boundary for {}", directory, ex);
			return false;
		}
	}

	private @Nullable Path discoveryBoundary(Path directory) {
		for (Path current = directory; current != null; current = current.getParent()) {
			if (Files.exists(current.resolve(".git"))) {
				return current;
			}
		}
		return directory.startsWith(this.workingDirectory) ? this.workingDirectory : null;
	}

	private Path normalize(Path target) {
		return (target.isAbsolute() ? target : this.workingDirectory.resolve(target)).toAbsolutePath().normalize();
	}

	private record FilesystemResult(List<AgentsMdResource> resources, AgentsMdResolutionOutcome outcome,
			long configuredLimit, boolean applicableDocumentFound) {

		private static FilesystemResult empty() {
			return new FilesystemResult(List.of(), AgentsMdResolutionOutcome.COMPLETE, 0, false);
		}
	}

	private record LoadedResource(@Nullable AgentsMdResource resource, boolean sizeLimit) {

		private static LoadedResource missing() {
			return new LoadedResource(null, false);
		}

		private static LoadedResource sizeExceeded() {
			return new LoadedResource(null, true);
		}
	}

}
