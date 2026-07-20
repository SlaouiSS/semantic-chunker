<!--
Thank you for contributing. Please make sure your change complies with the engineering constitution
(docs/CONTRIBUTING_ARCHITECTURE.md). The checklist below mirrors its Definition of Done (section 16).
-->

## Summary

<!-- What does this change do, and why? -->

## Type of change

- [ ] Bug fix
- [ ] New feature (within the fixed scope: semantic chunking)
- [ ] Documentation
- [ ] Build / tooling
- [ ] Refactor (no behavior change)

## Definition of Done

- [ ] Stays inside the fixed scope; does not drift toward a non-goal (§1).
- [ ] The core module imports no adapter, SDK, or parser type (§3.1.3).
- [ ] No new public type or SPI method without written justification and maintainer sign-off (§5.2.1).
- [ ] Implementation detail does not leak through a public signature (§5.2.3).
- [ ] Domain objects are immutable, defensively copied, records where appropriate; no `Optional`
      fields, no boolean-mode parameters (§7.1).
- [ ] Any touched SPI carries the full five-part contract (§6.1).
- [ ] Global-ordinal stability is preserved; windowing/merging changes have adversarial tests (§7.3).
- [ ] The failure model holds: a single bad window degrades and warns rather than throwing (§9).
- [ ] No static mutable state; dependencies are constructor-injected (§10).
- [ ] Tests are deterministic and require no live language model (§12).
- [ ] Every new public type has Javadoc; significant decisions have an ADR (§11).
- [ ] A Spring Framework maintainer, reading this change cold, would not object to it.

## Related issues

<!-- e.g. Closes #123 -->
