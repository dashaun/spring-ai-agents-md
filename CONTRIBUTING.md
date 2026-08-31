# Contributing to Spring AI AGENTS.md

Thank you for contributing to the Spring AI Community. Contributions are governed by
the organization-wide [Code of Conduct](https://github.com/spring-ai-community/.github/blob/main/CODE_OF_CONDUCT.md).

## Before You Start

- Search existing issues and pull requests before opening a new one.
- Use an issue to discuss significant features or public API changes before investing
  in an implementation.
- Do not use public issues for security vulnerabilities. Follow [SECURITY.md](SECURITY.md)
  instead.

## Development

This project requires Java 17 or newer and uses the Maven Wrapper.

```bash
./mvnw clean compile
./mvnw clean test
```

Before submitting a pull request, apply the project formatter and run the complete test
suite:

```bash
./mvnw spring-javaformat:apply
./mvnw clean test
```

Keep changes focused, add tests for changed behavior, and update documentation when a
change affects users.

## Commits and Pull Requests

- Sign every commit with a verified signature.
- Use concise, imperative commit subjects.
- Keep history linear; rebase instead of adding merge commits.
- Submit changes through a pull request. Direct changes to `main` are not accepted.
- Ensure all required status checks pass.
- Confirm that your contribution is compatible with the Apache License 2.0.

By submitting a contribution, you agree that it may be distributed under this project's
[Apache License 2.0](LICENSE).
