---
name: testfly-research
description: Research an external library, tool, protocol or standard for the TestFly Java SDK (Maven artifacts, versions, licensing, APIs, limits) from primary sources and return a short, cited report. Use before adopting a dependency or when an external system's behaviour is unclear; read-only.
---

# TestFly technical research

1. **Primary sources first:** official docs, the source repository (README, LICENSE, CHANGELOG, source) and Maven Central metadata. Blog posts are hints, not evidence. Local jar inspection (`~/.m2/repository`, `javap`) needs a shell: when you have no Bash tool, list the exact command under "Unverified, needs a local check" and let the lead run it.
2. **Pin facts to versions:** the latest release and its date, the version TestFly uses (`pom.xml`), and breaking changes in between.
3. **License:** check what it allows. Flag source-available licenses (FSL, BSL, SSPL). TestFly must not redistribute such binaries (see the mobilecli stance in ADR 0002).
4. **Fit:** prefer JDK facilities and existing dependencies (`AGENTS.md` invariants). Note when a new dependency would have to stay optional.
5. **Separate** observed facts from inferences.

## Report (keep it under ~25 lines)

- **Answer:** one or two sentences.
- **Facts:** bullets with a source link or a local file reference.
- **Risks / unknowns.**
- **Recommendation** for TestFly.
