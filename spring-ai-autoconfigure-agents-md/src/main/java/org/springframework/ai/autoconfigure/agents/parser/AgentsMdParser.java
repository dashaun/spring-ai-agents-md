package org.springframework.ai.autoconfigure.agents.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * Reads standard Markdown into an {@link AgentsMdDocument} without imposing a schema.
 */
public class AgentsMdParser {

	/**
	 * Parse an {@code AGENTS.md} resource as UTF-8.
	 * @param resource source resource
	 * @return parsed document
	 * @throws IOException when the resource cannot be read
	 */
	public AgentsMdDocument parse(Resource resource) throws IOException {
		Assert.notNull(resource, "Resource must not be null");
		try (InputStream inputStream = resource.getInputStream()) {
			return parse(inputStream);
		}
	}

	/**
	 * Parse an {@code AGENTS.md} input stream as UTF-8. The caller retains ownership of
	 * the stream.
	 * @param inputStream source stream
	 * @return parsed document
	 * @throws IOException when the stream cannot be read
	 */
	public AgentsMdDocument parse(InputStream inputStream) throws IOException {
		Assert.notNull(inputStream, "InputStream must not be null");
		return parse(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
	}

	/**
	 * Parse {@code AGENTS.md} Markdown content.
	 * @param content Markdown content
	 * @return parsed document
	 */
	public AgentsMdDocument parse(String content) {
		Assert.notNull(content, "Content must not be null");
		return new AgentsMdDocument(content);
	}

}
