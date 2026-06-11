---
name: check-docs
description: Verify current official documentation and real Maven Central versions before writing integration code for Spring Boot 4, Spring AI 2, Modulith 2, JobRunr or any fast-moving dependency. Use whenever touching framework integration points or upgrading dependencies.
---

# Check current docs first

This stack is too new to trust memory. Before writing integration code against
Spring Boot 4.x, Spring AI 2.x, Modulith 2.x, JobRunr 8.x, Testcontainers 2.x or
Tailwind 4.x APIs:

1. **Check the real artifact/version on Maven Central** (search API may be blocked;
   metadata works):
   ```bash
   curl -s "https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml" | grep -E "<(latest|release)>"
   ```
2. **Fetch the official docs** (WebFetch) for the exact major version:
   - Boot 4: https://docs.spring.io/spring-boot/index.html
   - Spring AI 2: https://docs.spring.io/spring-ai/reference/index.html
   - Modulith 2: https://docs.spring.io/spring-modulith/reference/index.html
3. **Distrust these legacy patterns** (they LOOK right but are wrong here):
   - Jackson 2 (`com.fasterxml.*`) → Boot 4 uses Jackson 3 (`tools.jackson.*`)
   - Spring AI 1.x packages/annotations → 2.0 changed them
   - `org.testcontainers:postgresql` → 2.x is `testcontainers-postgresql`
   - Spring Security 6 idioms → when auth arrives it will be Security 7
   - `spring-retry`, `io.spring.dependency-management` → not used here
4. If the POM of a Boot starter is in doubt, read it directly:
   ```bash
   curl -s "https://repo1.maven.org/maven2/org/springframework/boot/<artifact>/<ver>/<artifact>-<ver>.pom"
   ```
5. Record any newly-discovered gotcha in CLAUDE.md ("Version gotchas").
