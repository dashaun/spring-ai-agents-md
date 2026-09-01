package io.github.springaicommunity.agentsmd.codingagent;

import java.nio.file.Path;

record ChangeProposal(String id, Path path, String before, String after, String expectedDigest, boolean createsFile) {
}
