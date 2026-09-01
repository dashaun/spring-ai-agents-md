package io.github.springaicommunity.agentsmd.codingagent;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("steward")
record RepositoryStewardProperties(Path workspace, DataSize maxFileSize, int maxReadLines, int maxListEntries,
		int maxSearchMatches, int maxSearchDepth) {

	RepositoryStewardProperties {
		if (workspace == null) {
			workspace = Path.of(".");
		}
		if (maxFileSize == null) {
			maxFileSize = DataSize.ofKilobytes(128);
		}
		if (maxReadLines <= 0) {
			maxReadLines = 200;
		}
		if (maxListEntries <= 0) {
			maxListEntries = 200;
		}
		if (maxSearchMatches <= 0) {
			maxSearchMatches = 50;
		}
		if (maxSearchDepth <= 0) {
			maxSearchDepth = 12;
		}
	}

}
