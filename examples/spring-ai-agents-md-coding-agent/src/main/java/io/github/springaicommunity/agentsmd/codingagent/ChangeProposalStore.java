package io.github.springaicommunity.agentsmd.codingagent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
final class ChangeProposalStore {

	private final Map<String, ChangeProposal> proposals = new LinkedHashMap<>();

	private final AtomicInteger sequence = new AtomicInteger();

	private final RepositoryWorkspace workspace;

	ChangeProposalStore(RepositoryWorkspace workspace) {
		this.workspace = workspace;
	}

	synchronized ChangeProposal propose(Path path, String before, String after, boolean createsFile) {
		String id = "change-%03d".formatted(this.sequence.incrementAndGet());
		ChangeProposal proposal = new ChangeProposal(id, path, before, after, digest(before), createsFile);
		this.proposals.put(id, proposal);
		return proposal;
	}

	synchronized String list() {
		if (this.proposals.isEmpty()) {
			return "No pending changes.";
		}
		StringBuilder result = new StringBuilder("Pending changes:\n");
		for (ChangeProposal proposal : this.proposals.values()) {
			result.append(proposal.id())
				.append("  ")
				.append(proposal.createsFile() ? "create " : "modify ")
				.append(this.workspace.display(proposal.path()))
				.append('\n');
		}
		return result.toString().stripTrailing();
	}

	synchronized Set<String> ids() {
		return Set.copyOf(this.proposals.keySet());
	}

	synchronized String preview(String id) {
		ChangeProposal proposal = require(id);
		return "Proposal " + proposal.id() + "\n--- a/" + this.workspace.display(proposal.path()) + "\n+++ b/"
				+ this.workspace.display(proposal.path()) + "\n" + contextualDiff(proposal.before(), proposal.after());
	}

	synchronized String apply(String id) {
		ChangeProposal proposal = require(id);
		Path path = this.workspace.resolve(this.workspace.display(proposal.path()), proposal.createsFile());
		String current = readCurrent(path, proposal.createsFile());
		if (!digest(current).equals(proposal.expectedDigest())) {
			throw new IllegalStateException(
					"The file changed after this proposal was created; discard it and ask for a new proposal");
		}
		writeAtomically(path, proposal.after());
		this.proposals.remove(id);
		return "Applied " + id + " to " + this.workspace.display(path) + ".";
	}

	synchronized String discard(String id) {
		ChangeProposal proposal = require(id);
		this.proposals.remove(id);
		return "Discarded " + proposal.id() + "; no files were changed.";
	}

	private ChangeProposal require(String id) {
		ChangeProposal proposal = this.proposals.get(id);
		if (proposal == null) {
			throw new IllegalArgumentException("Unknown pending proposal: " + id);
		}
		return proposal;
	}

	private String readCurrent(Path path, boolean createsFile) {
		if (createsFile && !Files.exists(path)) {
			return "";
		}
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not verify the proposed file", ex);
		}
	}

	private void writeAtomically(Path path, String content) {
		Path temporary = null;
		try {
			Path parent = path.getParent();
			if (parent == null || !Files.isDirectory(parent)) {
				throw new IllegalStateException("The destination directory does not exist");
			}
			temporary = Files.createTempFile(parent, ".steward-", ".tmp");
			Files.writeString(temporary, content, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not apply the proposed change", ex);
		}
		finally {
			if (temporary != null) {
				try {
					Files.deleteIfExists(temporary);
				}
				catch (IOException ignored) {
					// The destination was already preserved; a temporary file may remain.
				}
			}
		}
	}

	private static String contextualDiff(String before, String after) {
		List<String> beforeLines = before.lines().toList();
		List<String> afterLines = after.lines().toList();
		int commonPrefix = 0;
		while (commonPrefix < beforeLines.size() && commonPrefix < afterLines.size()
				&& beforeLines.get(commonPrefix).equals(afterLines.get(commonPrefix))) {
			commonPrefix++;
		}
		int commonSuffix = 0;
		while (commonSuffix < beforeLines.size() - commonPrefix && commonSuffix < afterLines.size() - commonPrefix
				&& beforeLines.get(beforeLines.size() - 1 - commonSuffix)
					.equals(afterLines.get(afterLines.size() - 1 - commonSuffix))) {
			commonSuffix++;
		}

		int context = 3;
		int beforeChangeEnd = beforeLines.size() - commonSuffix;
		int afterChangeEnd = afterLines.size() - commonSuffix;
		int hunkStart = Math.max(0, commonPrefix - context);
		int beforeHunkEnd = Math.min(beforeLines.size(), beforeChangeEnd + context);
		int afterHunkEnd = Math.min(afterLines.size(), afterChangeEnd + context);
		int beforeCount = beforeHunkEnd - hunkStart;
		int afterCount = afterHunkEnd - hunkStart;
		int beforeStart = beforeCount == 0 ? 0 : hunkStart + 1;
		int afterStart = afterCount == 0 ? 0 : hunkStart + 1;

		StringBuilder result = new StringBuilder("@@ -").append(beforeStart)
			.append(',')
			.append(beforeCount)
			.append(" +")
			.append(afterStart)
			.append(',')
			.append(afterCount)
			.append(" @@\n");
		appendLines(result, beforeLines, hunkStart, commonPrefix, "  ");
		appendLines(result, beforeLines, commonPrefix, beforeChangeEnd, "- ");
		appendLines(result, afterLines, commonPrefix, afterChangeEnd, "+ ");
		appendLines(result, afterLines, afterChangeEnd, afterHunkEnd, "  ");
		return result.toString();
	}

	private static void appendLines(StringBuilder result, List<String> lines, int start, int end, String prefix) {
		for (int index = start; index < end; index++) {
			result.append(prefix).append(lines.get(index)).append('\n');
		}
	}

	private static String digest(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(bytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
