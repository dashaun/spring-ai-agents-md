# Spring AI AGENTS.md Repository Steward

The Repository Steward is an interactive coding-agent demo built with Spring AI, Spring
Shell 4.0.3, and the Spring AI AGENTS.md starter. It explores a repository, follows the
AGENTS.md instructions applicable to each file, and proposes reviewable changes. A model
cannot apply its own proposal: a person must run `apply-change` explicitly.

> **Video walkthrough:** [Build a Safe Coding Agent with Spring AI +
> AGENTS.md](https://youtu.be/bIlYmp_iiBM)

## What the demo proves

- Spring AI's `ChatClient` drives the recursive model/tool loop.
- The starter injects standard AGENTS.md documents without requiring a schema.
- Filesystem tools propagate their active path, so nested instructions apply as the
  steward moves through a repository.
- Spring Shell provides an interactive JLine CLI with history and completion.
- Reads and searches stay inside a bounded workspace.
- Model-generated edits remain pending until a person reviews and approves them.

## Architecture

```text
Spring Shell `steward` command
            |
            v
Spring AI ChatClient + ToolCallingAdvisor
            |
            +--> AgentsMdSystemAdvisor (refreshes instructions for active path)
            +--> listFiles / searchFiles / readFile
            +--> proposePatch --> pending ChangeProposal
                                      |
                         show-change / apply-change / discard-change
                                      |
                                      v
                              atomic filesystem write
```

## Prerequisites

- Java 17 or later
- Access to an Ollama server
- The `laguna-xs-2.1:q4_k_m` model available on that server

> **Model requirement:** Use a model that is good at multi-step structured tool calling.
> Advertising tool support is not sufficient: the model must inspect the file, preserve
> exact replacement text across tool calls, invoke `proposePatch`, and return to the
> conversation after the tool result.

## Run it

From the repository root:

```shell
./mvnw install -DskipTests
./mvnw -f examples/spring-ai-agents-md-coding-agent/pom.xml spring-boot:run
```

The application explicitly includes `spring-shell-jline`. Spring Shell 4 does not bring
the richer JLine implementation into its basic starter by default.

The demo's Maven configuration starts the application in the repository root, which is
also the default workspace. It can be configured together with the model:

```shell
./mvnw -f examples/spring-ai-agents-md-coding-agent/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--spring.ai.ollama.base-url=http://localhost:11434 --spring.ai.ollama.chat.model=laguna-xs-2.1:q4_k_m"
```

When launching the packaged JAR instead of Maven, launch from the same directory used as
the workspace so the first request has the right AGENTS.md scope. Filesystem tools then
update the active target as the steward explores nested directories.

## Shell commands

| Command | Purpose |
| :--- | :--- |
| `steward "<task>"` | Ask the coding agent to inspect or change the repository. |
| `workspace` | Show the contained filesystem root. |
| `instructions <path>` | Show applicable AGENTS.md locations, broadest first. |
| `changes` | List pending proposals. |
| `show-change <id>` | Display a proposal before approval. |
| `apply-change <id>` | Explicitly approve and atomically apply a proposal. |
| `discard-change <id>` | Remove a proposal without writing. |
| `help` | Show all available Spring Shell commands. |
| `exit` | Leave the interactive shell. |

## Suggested video walkthrough

Record from a clean checkout so each result is easy to explain and reproduce.

### 1. Establish the workspace and instruction hierarchy

```text
steward:> workspace
steward:> instructions AGENTS.md
steward:> instructions examples/spring-ai-agents-md-coding-agent/src/main/java/io/github/springaicommunity/agentsmd/codingagent/RepositoryTools.java
```

Call out that `instructions` reports locations, while AGENTS.md contents never go into
logs, metrics, or observation tags.

### 2. Let the agent explore

```text
steward:> steward "Explain how this coding agent prevents a model from writing outside the repository or approving its own changes. Inspect the implementation before answering."
```

This should exercise file listing, search, and reading before the model answers.

### 3. Ask for a predictable safe change

```text
steward:> steward "Read examples/spring-ai-agents-md-coding-agent/README.md. Then use proposePatch to add exactly one concise bullet under What the demo proves stating that stale proposals cannot overwrite files changed after review. Do not alter any other text."
```

The response should include a proposal ID and state that no file was changed.

### 4. Demonstrate human approval

```text
steward:> changes
steward:> show-change change-001
steward:> apply-change change-001
steward:> changes
```

Before `apply-change`, show that the working tree is unchanged. Afterwards, show the one
approved edit. Restore that demonstration edit before committing or recording again.

### 5. Optional: show the stale-review guard

Create another proposal, modify its target in a second terminal, and then run
`apply-change`. The steward refuses the write because the file digest no longer matches
the version that was reviewed.

## Filesystem safety model

- Tool paths must be relative to the configured workspace.
- Normalization rejects attempts to escape with `..`.
- `.git`, `.idea`, `.vscode`, and `target` segments are protected.
- Symbolic links are rejected and searches do not follow them.
- Only bounded UTF-8 text files are read or proposed.
- Listings, reads, searches, depth, and result counts have configurable limits.
- A proposed replacement must identify one unique text occurrence.
- Proposals retain a SHA-256 digest of the reviewed source.
- The digest is revalidated immediately before an atomic write.
- Applying and discarding are shell commands, not model tools.
- The agent has no shell-command, Git, network, delete, or unrestricted write tool.

## Configuration

| Property | Default | Description |
| :--- | :--- | :--- |
| `steward.workspace` | JVM working directory | Repository boundary for every tool. |
| `steward.max-file-size` | `128KB` | Maximum file read or proposed. |
| `steward.max-read-lines` | `200` | Maximum lines returned by one read. |
| `steward.max-list-entries` | `200` | Maximum entries returned by one listing. |
| `steward.max-search-matches` | `50` | Maximum literal-search matches. |
| `steward.max-search-depth` | `12` | Maximum recursive search depth. |

Standard Spring AI Ollama properties configure the model and server URL.

### Model selection

Use a model that reliably supports multi-step structured tool calling. Small general
models may stop after reading, print tool-shaped JSON, or invent a proposal ID instead
of invoking a tool. The steward cross-checks every response against its proposal store
and never exposes such an ID as valid. If `proposePatch` did not actually run, the
command reports that no proposal was created.

The demo defaults to `laguna-xs-2.1:q4_k_m`. The following models created a real proposal
in isolated runs of the documented proposal walkthrough:

| Model | Notes |
| :--- | :--- |
| `laguna-xs-2.1:q4_k_m` | Recommended default. |
| `qwen3:8b` | Viable smaller alternative. |
| `qwen3-coder:30b` | Reliable coding-oriented alternative. |
| `qwen3.6:35b` | Reliable tool use and strong explanations. |
| `muse-glimmer:30b-mlx` | Created a real proposal. |
| `laguna-s-2.1:latest` | Created a real proposal; very large. |
| `gpt-oss:20b` | Smallest successful model in its test group. |
| `qwen3.6:27b-mlx` | Created a real proposal. |

These are demo observations, not general model benchmarks. Model versions, prompts, and
runtime configuration can change behavior. Override the default for comparison without
editing the application configuration:

```shell
SPRING_AI_OLLAMA_CHAT_MODEL=qwen3:8b \
  ./mvnw -f examples/spring-ai-agents-md-coding-agent/pom.xml spring-boot:run
```

## Test it

The main build uses a mocked `ChatModel`; CI requires neither Ollama nor an API key.

```shell
./mvnw -pl examples/spring-ai-agents-md-coding-agent -am test
```

Tests cover application wiring, AGENTS.md prompt injection, traversal and symlink
rejection, explicit apply/discard behavior, atomic creation, and stale-proposal rejection.

## Deliberate limitations

Phase 2 does not execute build commands, mutate Git, delete files, access the network, or
run autonomously. AGENTS.md can tell the steward which verification command should be
run, but the developer remains responsible for running it. These boundaries keep the
AAIF AGENTS.md and Spring AI tool-calling demonstration focused and reviewable.
