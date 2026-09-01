# Spring AI AGENTS.md

[![CI](https://github.com/dashaun/spring-ai-agents-md/actions/workflows/ci.yml/badge.svg)](https://github.com/dashaun/spring-ai-agents-md/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/temurin/releases/?version=17)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.x-6DB33F.svg)](https://docs.spring.io/spring-boot/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.x-6DB33F.svg)](https://docs.spring.io/spring-ai/reference/)

Spring Boot auto-configuration for bringing instructions from
[`AGENTS.md`](https://agents.md) into Spring AI `ChatClient` requests.

`AGENTS.md` is a simple, open format for guiding coding agents. It is stewarded by the
[Agentic AI Foundation (AAIF)](https://aaif.io/) under the Linux Foundation.

> [!IMPORTANT]
> This project is under active development and has not published a release to Maven
> Central. Its APIs may change before the first stable release.

## Overview

An `AGENTS.md` file gives an agent a predictable place to find repository-specific
context and instructions. This project resolves applicable documents relative to the
request's target file, preserves their Markdown without imposing a schema, and adds them
to the chat request's system message through a Spring AI advisor.

The integration is model-provider neutral and uses Spring AI's `ChatClient` advisor API.

## Specification Alignment

The [AGENTS.md format](https://agents.md) is standard Markdown:

- It has no required fields or headings.
- Authors may organize instructions with any headings that fit their project.
- Instructions can cover build steps, tests, code style, pull-request expectations, or
  any other information useful to an agent.
- In a repository hierarchy, the closest `AGENTS.md` to the file being changed takes
  precedence, while explicit user instructions override file-based instructions.

The library does not assign special meaning to heading names or rewrite the document into
a project-specific schema. Each complete Markdown document is preserved unchanged inside
a small framework-owned context envelope that declares user and hierarchical precedence.

## Current Functionality

- Resolve filesystem `AGENTS.md` documents relative to a request target path.
- Walk toward the repository root and order applicable documents broadest-to-closest.
- Fall back to a classpath document when no filesystem document applies.
- Allow Spring AI filesystem tools to propagate their active path through `ToolContext`.
- Read an `AGENTS.md` from a `String`, `InputStream`, or Spring `Resource` as UTF-8.
- Preserve headings, prose, lists, tables, code blocks, whitespace, and line endings.
- Represent the complete document with an immutable Java 17 record.
- Provide an `AgentsMdSystemAdvisor` for Spring AI 2.x synchronous and streaming
  `ChatClient` calls.
- Automatically attach the advisor to Spring AI's auto-configured `ChatClient.Builder`.
- Auto-configure the reader, document, advisor, builder customizer, and configuration
  properties.
- Use JSpecify nullness annotations with non-null defaults across the public API.
- Use Jackson 3 through Spring Boot 4.1 dependency management.

## Requirements

| Technology | Supported version |
| :--- | :--- |
| Java | 17 or later |
| Spring Boot | 4.1.x |
| Spring AI | 2.x |
| Maven | Maven Wrapper included |

Spring Boot 3.x is not a supported target for this project.

## Getting Started

### Build the Snapshot Locally

Until artifacts are published, clone and install the snapshot with the included Maven
Wrapper:

```shell
git clone https://github.com/dashaun/spring-ai-agents-md.git
cd spring-ai-agents-md
./mvnw clean install
```

### Add the Dependency

```xml
<dependency>
    <groupId>io.github.spring-ai-community</groupId>
    <artifactId>spring-ai-starter-agents-md</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

The starter brings in the focused `spring-ai-autoconfigure-agents-md` module and the
Spring dependencies needed to activate it.

### Add AGENTS.md

Place `AGENTS.md` at the repository root. No property is required. For nested projects,
add another `AGENTS.md` in the subproject; applicable documents are supplied from
broadest scope to closest scope, and the closest instructions take precedence when they
conflict.

This hierarchical behavior follows the accumulation, directory jurisdiction, and
closest-scope precedence semantics described by the proposed
[AGENTS.md v1.1 clarification](https://github.com/agentsmd/agents.md/issues/135). The
clarification is currently a proposal rather than part of the adopted specification.
Instructions in sibling directories do not apply, and discovery does not traverse above
the repository boundary. When no repository boundary is present, discovery is confined
to the configured working directory.

If no filesystem document applies, the starter uses `classpath:AGENTS.md` as a fallback.

AGENTS.md accepts ordinary Markdown and does not require particular sections. For
example:

```markdown
# Sample AGENTS.md

## Dev environment tips

- Use Java 17 or later.
- Use the Maven Wrapper instead of a system Maven installation.

## Testing instructions

- Run `./mvnw clean test` before submitting changes.
- Add or update tests for changed behavior.

## PR instructions

- Keep pull requests focused.
- Sign every commit.
```

### Build a ChatClient

The starter automatically attaches `AgentsMdSystemAdvisor` to Spring AI's
auto-configured `ChatClient.Builder`. Build the client normally; no advisor registration
is required:

```java
@Configuration
class AiConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

}
```

Application code can then use the normal Spring AI API:

```java
@Service
class AssistantService {

    private final ChatClient chatClient;

    AssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    String ask(String request) {
        return this.chatClient.prompt()
            .user(request)
            .call()
            .content();
    }

}
```

When the target is known before a request, provide it explicitly:

```java
chatClient.prompt()
    .advisors(AgentsMdAdvisorParams.target(
        Path.of("src/main/java/com/example/Example.java")))
    .user(request)
    .call();
```

Filesystem tools can propagate the path they accessed for subsequent resolution:

```java
@Tool
String readFile(String path, ToolContext toolContext) {
    Path target = Path.of(path);
    AgentsMdAdvisorParams.propagateActivePath(toolContext, target);
    return Files.readString(target);
}
```

An explicit request target takes priority over a tool-propagated path. If neither is
present, the starter resolves from the JVM working directory.

Tool-propagated state belongs to the `ChatClient` built from that builder. Applications
issuing concurrent requests for different workspaces should pass an explicit target on
each request instead of relying on mutable active-path state.

Each advisor invocation resolves one active target. For operations spanning unrelated
subtrees, filesystem tools should propagate the path for each operation, or the caller
should issue separate requests with an explicit target for each subtree. The starter does
not merge instructions from unrelated target paths into one request.

## Configuration

| Property | Default | Description |
| :--- | :--- | :--- |
| `spring.ai.agents-md.enabled` | `true` | Enables AGENTS.md auto-configuration. |
| `spring.ai.agents-md.location` | unset | Explicit Spring resource that replaces target-aware discovery completely. |
| `spring.ai.agents-md.fallback-location` | `classpath:AGENTS.md` | Fallback consulted only when no filesystem document applies. |
| `spring.ai.agents-md.inject-into-system-prompt` | `true` | Creates and automatically attaches the `AgentsMdSystemAdvisor`. |
| `spring.ai.agents-md.max-depth` | `32` | Maximum number of directories inspected from the active target toward its boundary. |
| `spring.ai.agents-md.max-documents` | `16` | Maximum number of documents composed for one target. |
| `spring.ai.agents-md.max-document-size` | `64KB` | Maximum UTF-8 byte size of one document. |
| `spring.ai.agents-md.max-total-size` | `256KB` | Maximum UTF-8 byte size of the composed prompt context, including hierarchy headings. |

If neither an applicable filesystem document nor the fallback resource exists, prompt
augmentation is a no-op.

Setting `spring.ai.agents-md.location` disables filesystem traversal and classpath
fallback selection. The configured resource must exist and be readable.

Safety limits apply to filesystem documents, the classpath fallback, and an explicitly
configured location. Oversized documents are skipped whole rather than truncated. Once
the aggregate or document-count limit is reached, composition stops while preserving the
broadest-to-closest order of accepted documents. Invalid non-positive limits fail during
configuration binding.

## Live Reload

Resolution happens for every Advisor invocation without caching document content. Changes
to filesystem `AGENTS.md` files therefore apply to the next request or tool-loop pass;
no application restart or file watcher is required. When a filesystem tool propagates a
new active path, the advisor replaces its previously injected context with the newly
applicable documents.

Automatic attachment applies to Spring AI's auto-configured `ChatClient.Builder`.
Clients created directly with `ChatClient.builder(chatModel)` or
`ChatClient.create(chatModel)` bypass Spring Boot builder customizers and observability.

## Logging and Observability

The library uses SLF4J and Spring Boot's standard logging configuration. Document loading
is logged at DEBUG under:

```text
org.springframework.ai.autoconfigure.agents
```

Debug messages include only the selected resource location and character count. The
contents of `AGENTS.md` are never written to logs.

When an `ObservationRegistry` is available, the advisor emits this Micrometer
observation around prompt augmentation:

| Observation | Low-cardinality key | Values |
| :--- | :--- | :--- |
| `spring.ai.agents.md.advisor` | `spring.ai.agents.md.document.state` | `present`, `empty` |
| `spring.ai.agents.md.advisor` | `spring.ai.agents.md.document.count` | `zero`, `one`, `multiple` |
| `spring.ai.agents.md.advisor` | `spring.ai.agents.md.resolution.outcome` | `complete`, `depth-limit`, `document-limit`, `size-limit` |

When a `MeterRegistry` is available, the
`spring.ai.agents.md.context.size` distribution summary records the number of characters
added to the system prompt. It exposes aggregate count, total, and maximum values without
using context size or resource paths as tags.

When a safety limit is reached, the advisor also publishes one
`AgentsMdLimitReachedEvent` for that resolution. Interactive applications can listen for
the event and render an immediate warning without coupling the starter to a particular
terminal or user interface:

```java
@EventListener
void warnAboutAgentsMdLimit(AgentsMdLimitReachedEvent event) {
    System.err.printf(
        "AGENTS.md %s reached for %s; %d documents were applied.%n",
        event.outcome(), event.target(), event.acceptedDocumentCount());
}
```

The event contains the normalized target, resolution outcome, accepted document count,
injected UTF-8 context size, and configured limit. It never contains document contents.
Spring application events are synchronous by default, so listeners should return quickly
and must not throw exceptions. CLI listeners can rate-limit repeated notifications from
tool loops if needed.

The contextual name is `agents-md advisor`. The observation covers only the local prompt
augmentation step; it does not wrap the downstream model call or duplicate Spring AI's
model observations. No document contents or resource paths are added as observation
tags. If no registry is configured, instrumentation uses Micrometer's no-op registry.

## Building and Testing

Use the Maven Wrapper from the repository root:

### Project Structure

```text
spring-ai-agents-md/
├── examples/
│   ├── spring-ai-agents-md-example/          # Runnable Ollama web application
│   └── spring-ai-agents-md-coding-agent/     # Spring Shell repository steward
├── spring-ai-autoconfigure-agents-md/  # Auto-configuration, public API, and tests
├── spring-ai-starter-agents-md/        # Dependency-only starter for applications
└── pom.xml                             # Parent and reactor build
```

| Task | Command |
| :--- | :--- |
| Compile | `./mvnw clean compile` |
| Run all tests | `./mvnw clean test` |
| Run document reader tests | `./mvnw -pl spring-ai-autoconfigure-agents-md test -Dtest=AgentsMdParserTests` |
| Apply formatting | `./mvnw spring-javaformat:apply` |
| Analyze dependencies | `./mvnw dependency:analyze` |

Spring Java Format validation runs during Maven's `validate` phase.
JaCoCo runs during `verify` and requires at least 85% line coverage independently for the
parser and advisor packages.

## Example Applications

The [repository steward](examples/spring-ai-agents-md-coding-agent) is the Phase 2
foundation for an AGENTS.md-aware coding agent built with Spring AI and Spring Shell
4.0.3. It explicitly includes Spring Shell's JLine module for the richer interactive
experience. Bounded filesystem tools let the agent inspect a repository and create
reviewable change proposals; only explicit shell commands can apply or discard them.

The [Ollama example](examples/spring-ai-agents-md-example) is a real Spring Boot
application that depends on the starter through its public API. It demonstrates advisor
registration, a `/chat` endpoint, Actuator metrics, and zero-configuration filesystem
discovery of its project-level `AGENTS.md`.

Its normal test replaces the model with a mock. Pull-request CI uses a pinned WireMock
Testcontainer to validate the real Ollama HTTP request deterministically, including the
injected system message. A separate opt-in integration profile uses the pinned pre-warmed
`ghcr.io/dashaun/testcontainer-ollama-smollm2-135m:0.33.2` image from
[`dashaun/testcontainer-ollama-images`](https://github.com/dashaun/testcontainer-ollama-images).
See the example README for launch commands, architecture requirements, and the real-model
integration test.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull
request and follow the Spring AI Community
[Code of Conduct](https://github.com/spring-ai-community/.github/blob/main/CODE_OF_CONDUCT.md).

Before submitting changes:

```shell
./mvnw spring-javaformat:apply
./mvnw clean test
```

All commits must be signed. The `main` branch requires pull requests, a passing build,
and linear history.

## Security

Report suspected vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
Do not disclose security issues in a public GitHub issue.

## License

Spring AI AGENTS.md is available under the [Apache License 2.0](LICENSE).

## Links

- [AGENTS.md](https://agents.md)
- [Agentic AI Foundation](https://aaif.io/)
- [Spring AI documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring Boot documentation](https://docs.spring.io/spring-boot/)
- [Spring AI Community](https://github.com/spring-ai-community)
- [Issue tracker](https://github.com/dashaun/spring-ai-agents-md/issues)
