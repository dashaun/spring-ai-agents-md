package org.springframework.ai.autoconfigure.agents.config;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for AGENTS.md support.
 */
@ConfigurationProperties(prefix = "spring.ai.agents-md")
public class AgentsMdProperties {

	/** Whether AGENTS.md auto-configuration is enabled. */
	private boolean enabled = true;

	/**
	 * Explicit Spring resource location that replaces filesystem discovery and fallback
	 * selection completely.
	 */
	private @Nullable String location;

	/** Resource consulted only when no applicable filesystem AGENTS.md exists. */
	private String fallbackLocation = "classpath:AGENTS.md";

	/** Whether the resolved instructions are automatically attached to ChatClient. */
	private boolean injectIntoSystemPrompt = true;

	/** Maximum number of directories inspected from the target toward its boundary. */
	private int maxDepth = 32;

	/** Maximum number of AGENTS.md documents composed for one target. */
	private int maxDocuments = 16;

	/** Maximum UTF-8 byte size accepted for an individual AGENTS.md document. */
	private DataSize maxDocumentSize = DataSize.ofKilobytes(64);

	/**
	 * Maximum UTF-8 byte size of the complete injected context, including the precedence
	 * envelope and source headings.
	 */
	private DataSize maxTotalSize = DataSize.ofKilobytes(256);

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public @Nullable String getLocation() {
		return this.location;
	}

	public void setLocation(@Nullable String location) {
		if (location != null && location.isBlank()) {
			throw new IllegalArgumentException("location must not be blank");
		}
		this.location = location;
	}

	public String getFallbackLocation() {
		return this.fallbackLocation;
	}

	public void setFallbackLocation(String fallbackLocation) {
		this.fallbackLocation = Objects.requireNonNull(fallbackLocation, "fallbackLocation must not be null");
	}

	public boolean isInjectIntoSystemPrompt() {
		return this.injectIntoSystemPrompt;
	}

	public void setInjectIntoSystemPrompt(boolean injectIntoSystemPrompt) {
		this.injectIntoSystemPrompt = injectIntoSystemPrompt;
	}

	public int getMaxDepth() {
		return this.maxDepth;
	}

	public void setMaxDepth(int maxDepth) {
		if (maxDepth < 1) {
			throw new IllegalArgumentException("maxDepth must be greater than zero");
		}
		this.maxDepth = maxDepth;
	}

	public int getMaxDocuments() {
		return this.maxDocuments;
	}

	public void setMaxDocuments(int maxDocuments) {
		if (maxDocuments < 1) {
			throw new IllegalArgumentException("maxDocuments must be greater than zero");
		}
		this.maxDocuments = maxDocuments;
	}

	public DataSize getMaxDocumentSize() {
		return this.maxDocumentSize;
	}

	public void setMaxDocumentSize(DataSize maxDocumentSize) {
		this.maxDocumentSize = validSize(maxDocumentSize, "maxDocumentSize");
	}

	public DataSize getMaxTotalSize() {
		return this.maxTotalSize;
	}

	public void setMaxTotalSize(DataSize maxTotalSize) {
		this.maxTotalSize = validSize(maxTotalSize, "maxTotalSize");
	}

	private DataSize validSize(DataSize size, String name) {
		Objects.requireNonNull(size, name + " must not be null");
		if (size.toBytes() < 1 || size.toBytes() >= Integer.MAX_VALUE) {
			throw new IllegalArgumentException(name + " must be between 1 byte and 2 GB");
		}
		return size;
	}

}
