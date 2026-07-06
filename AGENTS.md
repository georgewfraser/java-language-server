# AGENTS

## Purpose

This repository contains a Java language server and a VS Code extension that packages and launches it.

- Java server code lives under `src/main/java/org/javacs`.
- VS Code extension code lives under `lib`.
- JUnit tests live under `src/test/java`.
- Test workspaces and fixtures live under `src/test/examples`.

When making changes, optimize for the behavior users see through the language server protocol rather than for internal structure.

## Product Goals

The primary goal of this project is robustness.

- The language server should be able to drop into a Java project with little or no manual configuration and figure out the project structure.
- When a feature conflicts with robustness, prefer the more robust behavior.
- Prefer degraded functionality over fragile behavior, crashes, or incorrect results.
- Avoid assumptions that only hold for a narrow set of build tools or project layouts unless the code has an explicit fallback.

## Environment

Use the repository's actual build scripts as the source of truth.

- Maven, npm, and `protoc` are required for normal development.
- Run `./configure` once per clone to install the repository git hook.
- `scripts/build.sh` bundles JDK 25 runtimes and is the authoritative packaging path.
- `README.md` mentions Java 25 for local setup.

## Repo Map

Important source areas:

- `src/main/java/org/javacs/completion`: completions and signature help support.
- `src/main/java/org/javacs/navigation`: go-to-definition and references.
- `src/main/java/org/javacs/hover`: hover and documentation lookup.
- `src/main/java/org/javacs/rewrite`: code actions and refactorings.
- `src/main/java/org/javacs/debug`: debug adapter support.
- `src/main/java/org/javacs/lsp`: protocol types and transport helpers.
- `lib`: VS Code extension entry points and editor integration.
- `src/test/java/org/javacs/LanguageServerFixture.java`: common server test harness.
- `src/test/java/org/javacs/lsp`: protocol-level tests.
- `src/test/examples`: workspace fixtures used by end-to-end tests.

## Code Style

Prefer simple, procedural code.

- Use straightforward control flow, explicit loops, and local variables.
- Avoid functional-style pipelines, stream-heavy code, callback-heavy code, and clever abstractions when a direct procedural implementation is clearer.
- Keep data flow easy to trace in a debugger.
- Write code that is easy to refactor without hidden coupling.
- Favor small, explicit helper methods over dense abstractions.

## Testing Philosophy

Prefer end-to-end tests that exercise behavior through the LSP interface.

- Most tests should validate externally visible behavior through the language server interface.
- Tests should be resilient to internal refactors and avoid coupling to implementation details.
- When fixing a user-visible bug, prefer an LSP-level regression test.
- True unit tests should be rare.
- Add a unit test only when the behavior cannot be tested reasonably through the LSP interface, or when the isolated case would be impractical to cover end-to-end.

Use the existing test harness where possible:

- `src/test/java/org/javacs/LanguageServerFixture.java`
- `src/test/java/org/javacs/lsp`

## Build And Validation

Choose the narrowest command set that covers the change, then report exactly what was run.

- Java-only changes: run `mvn test`.
- VS Code extension changes: run `npm test`.
- Cross-cutting Java and extension changes: run `mvn test` and `npm test`.
- Packaging or release changes: use `./scripts/build.sh`.
- Build a Java package without tests only when you specifically need the artifact: `mvn package -DskipTests`.

If a command fails because of an existing repo issue, capture that failure and report it clearly.

## Benchmarks

Performance matters, especially for core language-server operations.

- For code changes, run `./scripts/benchmark.sh` before and after the change and report the result or failure.
- If the benchmark does not exercise the changed area well, say so explicitly.
- Do not claim a performance improvement without benchmark output.
- Treat benchmark failures as real signal to report, not as a step to skip silently.

Current limitation:

- `scripts/benchmark.sh` now runs `BenchmarkParser` and `BenchmarkPruner`, but coverage is still narrow and does not yet exercise completion, navigation, indexing, or incremental compilation.

## Generated And Derived Files

Do not hand-edit generated artifacts unless the task is explicitly about regenerating them.

- Protobuf sources live in `src/main/protobuf`.
- Regenerate protobuf Java classes with `./scripts/gen_proto.sh`.
- Generated protobuf Java files under `src/main/java/com/google/devtools/build/lib` should be treated as generated output.
- Do not edit `out`, `target`, packaged JDKs under `jdks`, or packaged runtime trees under `dist` unless the task specifically requires it.
- Do not edit `build.vsix` manually.

## Formatting

Formatting is partially automated, but verify the actual tooling before relying on it.

- `./configure` installs the repository pre-commit hook from `git_hooks/pre-commit`.
- Java formatting in the hook uses `google-java-format` with AOSP style.
- `scripts/format.sh` references an older formatter jar name than the jar currently checked into `git_hooks`. Treat the hook as the more reliable source of truth.

## Change Boundaries

Keep changes focused and avoid incidental churn.

- Do not rewrite working code into a different style without a concrete reason.
- Do not introduce new configuration requirements if the same behavior can be inferred automatically.
- Be careful when changing files in `src/test/examples`; they are part of the end-to-end test surface.
- Preserve public protocol behavior unless the task explicitly requires a protocol-visible change.

## Known Gotchas

- The project spans Maven Java code and a VS Code extension; validate both sides when a change crosses that boundary.
- `scripts/build.sh` installs the built VSIX into the local VS Code instance as part of packaging.
- The forked Guava code under `src/main/java/org/javacs/guava` exists to avoid the external dependency and keep `jlink` working.
- Benchmark coverage is currently narrow, and the benchmark script is currently failing; do not hide that fact in change reports.

## Release Process

Run `scripts/release.sh` — it bumps the patch version, pushes, and force-pushes the `release` tag, which triggers `.github/workflows/release.yml` to build and publish to the VS Code Marketplace. See the workflow file for CI details.
