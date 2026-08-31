# Spring AI AGENTS.md Ollama Example

This runnable Spring Boot application demonstrates the published starter API with a local
Ollama model. Its filesystem `AGENTS.md` is discovered from the example directory and
added to every chat request by
`AgentsMdSystemAdvisor`. The application builds its `ChatClient` normally; the starter
attaches the advisor to Spring AI's auto-configured builder.

The regular unit test uses a mocked `ChatModel`, so the main build remains fast and does
not require Docker. A separate integration test uses the pre-warmed
`ghcr.io/dashaun/testcontainer-ollama-smollm2-135m:0.33.2` image, preloaded with
`smollm2:135m-instruct-q4_0`. The published image is currently Linux AMD64, so
Docker Desktop uses emulation on Apple Silicon.

Required CI also runs a deterministic Ollama HTTP protocol test against the pinned,
multi-platform `wiremock/wiremock:3.13.1` image. Its request matcher rejects `/api/chat`
calls that do not contain both a system message and the example's `AGENTS.md` instruction.

## Run with the Testcontainer

Docker must be running. From the repository root, first install the reactor snapshot:

```shell
./mvnw install -DskipTests
```

Then launch the test application:

```shell
./mvnw -f examples/spring-ai-agents-md-example/pom.xml spring-boot:test-run \
  -Dspring-boot.run.main-class=io.github.springaicommunity.agentsmd.example.TestAgentsMdExampleApplication
```

Send a request:

```shell
curl --request POST http://localhost:8080/chat \
  --header 'Content-Type: application/json' \
  --data '{"message":"In one sentence, confirm that you are ready."}'
```

The request sent to Ollama includes the applicable filesystem `AGENTS.md` as a system message. Model
responses are nondeterministic; the WireMock integration test verifies that injected
request content exactly.

## Run the Integration Test

Run the deterministic protocol test used by pull-request CI:

```shell
./mvnw -pl examples/spring-ai-agents-md-example -am verify -Pwiremock-integration
```

Run the real Ollama model test:

```shell
./mvnw -pl examples/spring-ai-agents-md-example -am verify -Pollama-integration
```

The real-model test requires a non-empty response and has a five-minute timeout so an
underpowered runner fails clearly. Exact instruction injection is covered by the
deterministic protocol test. This real-model profile is intentionally separate from
pull-request CI.
