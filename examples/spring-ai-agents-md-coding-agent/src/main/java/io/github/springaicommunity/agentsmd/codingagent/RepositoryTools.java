package io.github.springaicommunity.agentsmd.codingagent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.ai.autoconfigure.agents.advisor.AgentsMdAdvisorParams;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
final class RepositoryTools {

	private final RepositoryWorkspace workspace;

	private final ChangeProposalStore proposals;

	RepositoryTools(RepositoryWorkspace workspace, ChangeProposalStore proposals) {
		this.workspace = workspace;
		this.proposals = proposals;
	}

	@Tool(name = "listFiles", description = "List files and directories within the repository workspace")
	String listFiles(@ToolParam(description = "Repository-relative directory, or . for the workspace root") String path,
			ToolContext toolContext) {
		Path directory = this.workspace.resolve(path, false);
		propagate(toolContext, directory);
		if (!Files.isDirectory(directory)) {
			return "Cannot list the path because it is not a directory.";
		}
		try (Stream<Path> entries = Files.list(directory)) {
			List<Path> accepted = entries.filter(this::isVisible)
				.sorted(Comparator.comparing(candidate -> candidate.getFileName().toString()))
				.limit(this.workspace.properties().maxListEntries() + 1L)
				.toList();
			boolean limited = accepted.size() > this.workspace.properties().maxListEntries();
			if (limited) {
				accepted = accepted.subList(0, this.workspace.properties().maxListEntries());
			}
			StringBuilder result = new StringBuilder();
			for (Path entry : accepted) {
				result.append(Files.isDirectory(entry) ? "directory " : "file      ")
					.append(this.workspace.display(entry))
					.append('\n');
			}
			if (limited) {
				result.append("Result stopped at the configured entry limit.\n");
			}
			return result.isEmpty() ? "The directory is empty." : result.toString().stripTrailing();
		}
		catch (IOException ex) {
			return "The directory could not be listed.";
		}
	}

	@Tool(name = "readFile", description = "Read a bounded range of lines from a UTF-8 text file in the repository")
	String readFile(@ToolParam(description = "Repository-relative file path") String path,
			@ToolParam(description = "First line to read, starting at 1", required = false) Integer startLine,
			@ToolParam(description = "Maximum number of lines to return", required = false) Integer lineCount,
			ToolContext toolContext) {
		Path file = this.workspace.resolve(path, false);
		propagate(toolContext, file);
		if (!Files.isRegularFile(file) || isTooLarge(file)) {
			return "The path is not a readable text file within the configured size limit.";
		}
		int first = startLine == null ? 1 : Math.max(startLine, 1);
		int count = lineCount == null || lineCount <= 0 ? this.workspace.properties().maxReadLines()
				: Math.min(lineCount, this.workspace.properties().maxReadLines());
		try {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			if (first > lines.size()) {
				return "The requested first line is past the end of the file.";
			}
			StringBuilder result = new StringBuilder();
			int end = Math.min(lines.size(), first - 1 + count);
			for (int index = first - 1; index < end; index++) {
				result.append(index + 1).append(": ").append(lines.get(index)).append('\n');
			}
			if (end < lines.size()) {
				result.append("More lines are available; continue at line ").append(end + 1).append(".\n");
			}
			return result.toString().stripTrailing();
		}
		catch (IOException ex) {
			return "The file could not be read as UTF-8 text.";
		}
	}

	@Tool(name = "searchFiles", description = "Search UTF-8 repository files for literal text")
	String searchFiles(@ToolParam(description = "Literal text to find") String query,
			@ToolParam(description = "Repository-relative directory to search") String path, ToolContext toolContext) {
		if (query == null || query.isBlank()) {
			return "A non-empty literal search query is required.";
		}
		Path directory = this.workspace.resolve(path, false);
		propagate(toolContext, directory);
		if (!Files.isDirectory(directory)) {
			return "The search path is not a directory.";
		}
		List<String> matches = new ArrayList<>();
		try (Stream<Path> candidates = Files.walk(directory, this.workspace.properties().maxSearchDepth())) {
			for (Path candidate : candidates.filter(this::isSearchable).sorted().toList()) {
				findMatches(candidate, query, matches);
				if (matches.size() >= this.workspace.properties().maxSearchMatches()) {
					break;
				}
			}
		}
		catch (IOException | RuntimeException ex) {
			return "The repository search could not be completed safely.";
		}
		if (matches.isEmpty()) {
			return "No matches found.";
		}
		String result = String.join(System.lineSeparator(), matches);
		if (matches.size() >= this.workspace.properties().maxSearchMatches()) {
			result += System.lineSeparator() + "Search stopped at the configured match limit.";
		}
		return result;
	}

	@Tool(name = "proposePatch",
			description = "Propose one exact text replacement in a repository file without modifying it")
	String proposePatch(@ToolParam(description = "Repository-relative destination file") String path, @ToolParam(
			description = "Exact existing text to replace, or empty only when creating a new file") String oldText,
			@ToolParam(description = "Replacement text, or complete content for a new file") String newText,
			ToolContext toolContext) {
		Path file = this.workspace.resolve(path, true);
		propagate(toolContext, file);
		boolean createsFile = !Files.exists(file);
		if (createsFile && !oldText.isEmpty()) {
			return "A new-file proposal must use empty existing text.";
		}
		if (!createsFile && !Files.isRegularFile(file)) {
			return "The proposed destination is not a regular file.";
		}
		String current;
		try {
			current = createsFile ? "" : Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			return "The current file could not be verified as UTF-8 text.";
		}
		String proposed;
		if (createsFile) {
			proposed = newText;
		}
		else {
			if (oldText.isEmpty()) {
				return "Existing-file proposals require non-empty text to replace.";
			}
			int first = current.indexOf(oldText);
			if (first < 0) {
				return "The exact text to replace was not found; read the file again before proposing a change.";
			}
			if (current.indexOf(oldText, first + oldText.length()) >= 0) {
				return "The exact text appears more than once; include more surrounding context so the replacement is unique.";
			}
			proposed = current.substring(0, first) + newText + current.substring(first + oldText.length());
		}
		if (proposed.getBytes(StandardCharsets.UTF_8).length > this.workspace.properties().maxFileSize().toBytes()) {
			return "The proposed file exceeds the configured size limit.";
		}
		if (current.equals(proposed)) {
			return "The proposal does not change the file.";
		}
		ChangeProposal proposal = this.proposals.propose(file, current, proposed, createsFile);
		return "Created " + proposal.id() + " for " + this.workspace.display(file)
				+ ". No file was changed. The user can run show-change " + proposal.id() + " and apply-change "
				+ proposal.id() + ".";
	}

	private void findMatches(Path file, String query, List<String> matches) {
		try {
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			for (int index = 0; index < lines.size(); index++) {
				if (lines.get(index).contains(query)) {
					matches.add(this.workspace.display(file) + ":" + (index + 1) + ": " + lines.get(index).strip());
					if (matches.size() >= this.workspace.properties().maxSearchMatches()) {
						return;
					}
				}
			}
		}
		catch (IOException | RuntimeException ignored) {
			// Binary and malformed text files are not useful search results.
		}
	}

	private boolean isSearchable(Path path) {
		return isVisible(path) && Files.isRegularFile(path) && !isTooLarge(path);
	}

	private boolean isVisible(Path path) {
		try {
			this.workspace.resolve(this.workspace.display(path), false);
			return true;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private boolean isTooLarge(Path path) {
		try {
			return Files.size(path) > this.workspace.properties().maxFileSize().toBytes();
		}
		catch (IOException ex) {
			return true;
		}
	}

	private static void propagate(ToolContext toolContext, Path target) {
		AgentsMdAdvisorParams.propagateActivePath(toolContext, target);
	}

}
