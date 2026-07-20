# Contributing to semantic-chunker

Thank you for your interest in contributing. This project holds itself to a defined set of
engineering standards, and every contribution is expected to follow them.

## Before you start

Read the design documentation — it is the specification, and it is treated as the source of truth:

- [VISION](docs/VISION.md), [ARCHITECTURE](docs/ARCHITECTURE.md), [MODEL](docs/MODEL.md),
  [API](docs/API.md), [PIPELINE](docs/PIPELINE.md), [SPI](docs/SPI.md), [PROMPT](docs/PROMPT.md).
- **[CONTRIBUTING_ARCHITECTURE.md](docs/CONTRIBUTING_ARCHITECTURE.md) — the engineering
  constitution.** It is binding. Every pull request must comply with it, and its
  [Definition of Done](docs/CONTRIBUTING_ARCHITECTURE.md#16-definition-of-done) is the checklist
  reviewers use.

The bar is explicit: the code should be understandable and acceptable to a maintainer of the Spring
Framework.

## Prerequisites

- **JDK 21** (the project baseline).
- No local Maven install is required — use the bundled Maven Wrapper (`./mvnw`).

## Building and verifying

```bash
./mvnw verify          # compile, run tests, enforce rules, check formatting, produce coverage
./mvnw spotless:apply  # auto-format sources before committing
```

The build runs, in every module:

- **Maven Enforcer** — build-environment rules (Java 21, Maven version, dependency hygiene).
- **Spotless** (google-java-format, AOSP) — the formatting gate; run `spotless:apply` before pushing.
- **Surefire / Failsafe** — unit and integration tests.
- **JaCoCo** — coverage reporting.

## Scope discipline

This library does exactly one thing: high-quality semantic chunking of documents using a language
model. Changes that pull it toward a non-goal (a RAG framework, a parser, an OCR engine, an
embedding/retrieval/reranking library, or a general-purpose chunking toolkit) are out of scope
regardless of technical merit. See the constitution, sections 1 and 10.

## Pull requests

- Keep the public API and the two SPIs stable; they are ten-year commitments. New public types or SPI
  methods require a written justification and maintainer sign-off.
- Domain objects are immutable, defensively copied, and use records where a record fits.
- Preserve the global-ordinal invariant; any change to windowing or merging must be covered by
  adversarial tests.
- Tests must be deterministic and require no live language model.
- Significant or contested decisions are recorded as Architecture Decision Records.

By contributing, you agree that your contributions are licensed under the project's
[Apache-2.0](LICENSE) license.
