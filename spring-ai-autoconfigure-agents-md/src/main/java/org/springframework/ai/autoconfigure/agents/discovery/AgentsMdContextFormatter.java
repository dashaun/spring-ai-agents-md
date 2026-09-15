package org.springframework.ai.autoconfigure.agents.discovery;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Formats applicable {@code AGENTS.md} resources as system-prompt context and computes
 * the exact UTF-8 byte size of that context without building the string.
 */
final class AgentsMdContextFormatter {

	private static final String PREAMBLE = "# AGENTS.md instructions\n\n"
			+ "Apply these project instructions to the current task. When an explicit instruction in the current "
			+ "user request conflicts with these instructions, follow the explicit user instruction.\n";

	private static final String MULTI_DOCUMENT = "\nDocuments are ordered from broadest scope to closest scope. "
			+ "When document instructions conflict, the closest document takes precedence.\n";

	private static final String HEADING_PREFIX = "\n## ";

	private static final String HEADING_SUFFIX = "\n\n";

	private static final long PREAMBLE_BYTES = PREAMBLE.getBytes(StandardCharsets.UTF_8).length;

	private static final long MULTI_DOCUMENT_BYTES = MULTI_DOCUMENT.getBytes(StandardCharsets.UTF_8).length;

	private static final long HEADING_PREFIX_BYTES = HEADING_PREFIX.getBytes(StandardCharsets.UTF_8).length;

	private static final long HEADING_SUFFIX_BYTES = HEADING_SUFFIX.getBytes(StandardCharsets.UTF_8).length;

	private AgentsMdContextFormatter() {
	}

	static String format(List<AgentsMdResource> resources) {
		if (resources.isEmpty()) {
			return "";
		}
		StringBuilder context = new StringBuilder(PREAMBLE);
		if (resources.size() > 1) {
			context.append(MULTI_DOCUMENT);
		}
		for (AgentsMdResource resource : resources) {
			context.append(HEADING_PREFIX)
				.append(resource.location())
				.append(HEADING_SUFFIX)
				.append(resource.document().content());
		}
		return context.toString();
	}

	static long byteSize(List<AgentsMdResource> resources) {
		if (resources.isEmpty()) {
			return 0;
		}
		long size = PREAMBLE_BYTES;
		if (resources.size() > 1) {
			size += MULTI_DOCUMENT_BYTES;
		}
		for (AgentsMdResource resource : resources) {
			size += documentBytes(resource);
		}
		return size;
	}

	static long preambleBytes() {
		return PREAMBLE_BYTES;
	}

	static long multiDocumentBytes() {
		return MULTI_DOCUMENT_BYTES;
	}

	static long documentBytes(AgentsMdResource resource) {
		return HEADING_PREFIX_BYTES + resource.location().getBytes(StandardCharsets.UTF_8).length + HEADING_SUFFIX_BYTES
				+ resource.document().content().getBytes(StandardCharsets.UTF_8).length;
	}

}
