package io.github.springaicommunity.agentsmd.codingagent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.autoconfigure.agents.discovery.AgentsMdResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
class RepositoryStewardCommands {

	private final ChatClient chatClient;

	private final ToolCallbackProvider toolCallbacks;

	private final ChangeProposalStore proposals;

	private final RepositoryWorkspace workspace;

	private final AgentsMdResolver agentsMdResolver;

	RepositoryStewardCommands(ChatClient chatClient, ToolCallbackProvider toolCallbacks, ChangeProposalStore proposals,
			RepositoryWorkspace workspace, AgentsMdResolver agentsMdResolver) {
		this.chatClient = chatClient;
		this.toolCallbacks = toolCallbacks;
		this.proposals = proposals;
		this.workspace = workspace;
		this.agentsMdResolver = agentsMdResolver;
	}

	@Command(name = "steward", description = "Ask the AGENTS.md-aware repository steward")
	String steward(@Argument(index = 0, description = "The repository question or coding task") String request) {
		Set<String> before = this.proposals.ids();
		String response = this.chatClient.prompt()
			.tools((Object[]) this.toolCallbacks.getToolCallbacks())
			.user(request)
			.call()
			.content();
		Set<String> created = new LinkedHashSet<>(this.proposals.ids());
		created.removeAll(before);
		if (!created.isEmpty()) {
			return created.stream()
				.map(id -> "Created " + id + ". No file was changed. Review it with `show-change " + id
						+ "` and approve it with `apply-change " + id + "`.")
				.collect(Collectors.joining(System.lineSeparator()));
		}
		if (request.toLowerCase(Locale.ROOT).contains("propos")) {
			return "No proposal was created. The model returned without successfully calling `proposePatch`; "
					+ "no proposal ID is valid. Try again with a model that reliably supports structured tool calling.";
		}
		return response;
	}

	@Command(name = "workspace", description = "Show the repository workspace")
	String workspace() {
		return this.workspace.root().toString();
	}

	@Command(name = "instructions", description = "Show which AGENTS.md files apply to a path")
	String instructions(@Argument(index = 0, description = "Repository-relative file or directory") String path) {
		var target = this.workspace.resolve(path, false);
		var resolution = this.agentsMdResolver.resolve(target);
		if (resolution.resources().isEmpty()) {
			return "No AGENTS.md files apply to " + this.workspace.display(target) + ".";
		}
		return resolution.resources()
			.stream()
			.map(resource -> resource.location().toString())
			.collect(Collectors.joining(System.lineSeparator(), "Applicable AGENTS.md files (broadest first):\n", ""));
	}

	@Command(name = "changes", description = "List pending change proposals")
	String changes() {
		return this.proposals.list();
	}

	@Command(name = "show-change", description = "Show a proposed change")
	String showChange(@Argument(index = 0, description = "Proposal ID") String id) {
		return this.proposals.preview(id);
	}

	@Command(name = "apply-change", description = "Explicitly approve and apply a proposed change")
	String applyChange(@Argument(index = 0, description = "Proposal ID") String id) {
		return this.proposals.apply(id);
	}

	@Command(name = "discard-change", description = "Discard a proposed change without applying it")
	String discardChange(@Argument(index = 0, description = "Proposal ID") String id) {
		return this.proposals.discard(id);
	}

}
