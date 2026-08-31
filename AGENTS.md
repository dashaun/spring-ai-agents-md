# AGENTS.md — spring-ai-agents-md

## Project overview

You are working on `spring-ai-agents-md`, an open-source Spring Boot 4.1 starter for
Spring AI 2.x. The library loads standard AGENTS.md documents without imposing a schema
and provides a Spring AI `CallAdvisor` that can add the complete document to `ChatClient`
requests.

---

## Development commands

Run these exact commands from the repository root:

- **Build project:** `./mvnw clean compile`
- **Run all tests:** `./mvnw clean test`
- **Run single test:**
  `./mvnw -pl spring-ai-autoconfigure-agents-md test -Dtest=AgentsMdParserTests`
- **Apply code formatting:** `./mvnw spring-javaformat:apply`
- **Validate dependencies:** `./mvnw dependency:analyze`

---

## Required practices

- Run `./mvnw spring-javaformat:apply` before finishing any code modifications.
- Keep test coverage above 85% for document loading and advisor logic.
- Keep imports compliant with Java 17 standards; do not use `sun.misc.*` or deprecated
  Spring AI APIs.
- Use `@AutoConfiguration` for Spring Boot auto-configuration classes.
- Never write AGENTS.md contents to logs, metrics, observation tags, or exception
  messages.
- Keep observation tags low-cardinality and use Spring Boot's configured
  `ObservationRegistry` when available.

## Changes that require discussion

- Ask before adding external dependencies to `pom.xml` outside of
  `org.springframework.ai` or standard Spring Boot starters.
- Ask before making breaking API changes to public classes such as `AgentsMdDocument` or
  `AgentsMdSystemAdvisor`.

## Prohibited changes

- Do not modify or delete `LICENSE` (Apache 2.0).
- Do not commit hardcoded LLM API keys or secrets in test files; use test mocks or
  environment variables.
- Do not add a project-specific schema or required headings to AGENTS.md content.

---

## Code Style & Architecture Guidelines

### 1. Spring AI 2.x idioms

- Use `CallAdvisor` for synchronous context injection instead of deprecated APIs.

### 2. Java & Package Structure
- Follow standard Spring package conventions: `org.springframework.ai.autoconfigure.agents.*`.
- Use Java records for immutable data carrier models such as `AgentsMdDocument`.

### 3. Verification Protocol
After making code changes, run this sequence to guarantee a clean state:
1. `./mvnw spring-javaformat:apply`
2. `./mvnw clean test`
