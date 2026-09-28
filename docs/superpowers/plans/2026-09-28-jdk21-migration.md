# Java 21 (JDK 21 LTS) Migration & Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the TestFly framework baseline from Java 17 to Java 21 LTS (`--release 21`), and modernize core framework capabilities by harnessing Virtual Threads (Project Loom), Sequenced Collections (JEP 431), and Switch Pattern Matching (JEP 441).

**Architecture:**

1. Update `pom.xml` build configuration to compile with `<release>21</release>` and upgrade `maven-compiler-plugin` to `3.13.0`.
2. Update agent constitutional rules (`AGENTS.md`, `GEMINI.md`) and project documentation to reflect Java 21 baseline.
3. Integrate Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) into `JdkLoadEngine` and async execution, enabling massive concurrency with negligible memory footprint.
4. Enhance `Locator` and collections with Sequenced Collections (`getFirst()`, `getLast()`, `first()`, `last()`).
5. Modernize semantic selector and locator resolution with Java 21 exhaustive Switch Expressions.

**Tech Stack:**

- Java 21 (OpenJDK 21 LTS / Eclipse Temurin)
- Apache Maven 3.9+ / Maven Compiler Plugin 3.13.0
- Selenium Java 4.48.0
- TestNG 7.9.0 / Cucumber 7.20.1 / Mockito 5.11.0 / JaCoCo 0.8.12

**Spec:** Baseline upgrade requested by user to leverage JDK 21 capabilities in TestFly.

## Global Constraints

- Target compilation: `--release 21`.
- Backward compatibility: Existing `@TestFlyApi` signatures must remain intact; new capabilities are additive.
- Zero static `WebDriver` state: All driver management continues through `DriverManager.getDriver()` ThreadLocal.
- Unit tests run headless with TestNG + Mockito (no real browser sessions in unit tests).

---

### Task 1: POM & Toolchain Baseline Upgrade to Java 21

**Files:**

- Modify: `pom.xml:39-44`, `pom.xml:300-307`
- Test: Terminal command `mvn clean test-compile`

**Interfaces:**

- Consumes: Java 21 compiler
- Produces: Bytecode version 65 (Java 21) across all classes

- [ ] **Step 1: Update Maven compiler properties and plugin in `pom.xml`**

Change `pom.xml`:

```xml
    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
```

and update `maven-compiler-plugin`:

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>21</release>
                </configuration>
            </plugin>
```

- [ ] **Step 2: Verify compilation with Java 21**

Run:

```bash
mvn clean test-compile
```

Expected: `BUILD SUCCESS` with `javac [debug release 21]`.

- [ ] **Step 3: Run existing unit test suite to verify Java 21 runtime parity**

Run:

```bash
mvn test
```

Expected: All tests pass under Java 21 runtime without bytecode or reflection errors.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: upgrade project baseline and compiler release to Java 21"
```

---

### Task 2: Documentation & Agent Rules Baseline Update

**Files:**

- Modify: `AGENTS.md`
- Modify: `GEMINI.md`
- Modify: `README.md`
- Modify: `.agents/MAP.md`

**Interfaces:**

- Consumes: Java 21 baseline decision
- Produces: Synchronized instructions preventing AI agents from reverting to Java 17

- [ ] **Step 1: Update `AGENTS.md` and `GEMINI.md`**

In `AGENTS.md`:
Replace:

```markdown
- **Java baseline:** 17 (compiled with `--release 17`)
```

With:

```markdown
- **Java baseline:** 21 (compiled with `--release 21`)
```

In `GEMINI.md`:
Replace:

```markdown
- **Java Baseline:** Java 17 (`--release 17`).
```

With:

```markdown
- **Java Baseline:** Java 21 (`--release 21`).
```

- [ ] **Step 2: Update `README.md` system requirements**

Ensure any mention of Java 17 is updated to Java 21 LTS.

- [ ] **Step 3: Verify git diff for documentation integrity**

Run:

```bash
git diff AGENTS.md GEMINI.md README.md
```

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md GEMINI.md README.md .agents/
git commit -m "docs: update framework baseline to Java 21 in agent rules and documentation"
```

---

### Task 3: Harness Virtual Threads (Project Loom) in Load Testing & Concurrency

**Files:**

- Modify: `src/main/java/io/testfly/loadtest/internal/JdkLoadEngine.java`
- Modify: `src/main/java/io/testfly/config/ExecutionConfig.java`
- Test: `src/test/java/io/testfly/unit/loadtest/JdkLoadEngineTest.java`

**Interfaces:**

- Consumes: `Executors.newVirtualThreadPerTaskExecutor()` (Java 21 stdlib)
- Produces: Virtual thread executor replacing OS platform thread pool for load simulation

- [ ] **Step 1: Write unit test validating virtual thread execution in `JdkLoadEngineTest`**

In `src/test/java/io/testfly/unit/loadtest/JdkLoadEngineTest.java`, add test asserting execution succeeds when simulating concurrent virtual users.

- [ ] **Step 2: Run test to observe baseline behavior**

Run:

```bash
mvn test -Dtest=JdkLoadEngineTest
```

Expected: PASS

- [ ] **Step 3: Implement Virtual Thread executor in `JdkLoadEngine.java`**

In `src/main/java/io/testfly/loadtest/internal/JdkLoadEngine.java:122`:
Replace:

```java
ExecutorService executor = Executors.newFixedThreadPool(users);
```

With:

```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

- [ ] **Step 4: Run test to verify virtual thread execution passes**

Run:

```bash
mvn test -Dtest=JdkLoadEngineTest
```

Expected: PASS with lightweight virtual threads.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/testfly/loadtest/internal/JdkLoadEngine.java
git commit -m "perf: leverage Java 21 Virtual Threads in JdkLoadEngine for ultra-scalable load testing"
```

---

### Task 4: Modernize Locator with Sequenced Collections (`getFirst`, `getLast`, `first()`, `last()`)

**Files:**

- Modify: `src/main/java/io/testfly/locator/Locator.java`
- Test: `src/test/java/io/testfly/unit/locator/LocatorTest.java`

**Interfaces:**

- Consumes: `java.util.List.getFirst()`, `java.util.List.getLast()` (Sequenced Collections JEP 431)
- Produces: Fluent methods `Locator.first()` and `Locator.last()`

- [ ] **Step 1: Write failing unit tests for `first()` and `last()` in `LocatorTest.java`**

```java
@Test
public void testFirstAndLastMethods() {
    Locator locator = Locator.css(".item");
    assertNotNull(locator.first());
    assertNotNull(locator.last());
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
mvn test -Dtest=LocatorTest#testFirstAndLastMethods
```

Expected: Compilation error (`cannot find symbol method first() / last()`).

- [ ] **Step 3: Implement `first()`, `last()`, and Sequenced Collections usage in `Locator.java`**

Add fluent selectors:

```java
    private boolean selectLast = false;

    /** Narrows to the first matching element. */
    public Locator first() {
        return nth(0);
    }

    /** Narrows to the last matching element. */
    public Locator last() {
        this.selectLast = true;
        return this;
    }
```

And inside `resolve()`:

```java
    List<WebElement> elements = resolveAll();
    if (elements.isEmpty()) {
        throw new LocatorException("No elements found for: " + this);
    }
    if (selectLast) {
        return elements.getLast(); // Java 21 SequencedCollection
    }
    if (nthIndex != null) {
        if (nthIndex >= elements.size()) {
            throw new LocatorException("Index " + nthIndex + " out of bounds (found " + elements.size() + ")");
        }
        return elements.get(nthIndex);
    }
    return elements.getFirst(); // Java 21 SequencedCollection
```

- [ ] **Step 4: Run test to verify passes**

Run:

```bash
mvn test -Dtest=LocatorTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/testfly/locator/Locator.java src/test/java/io/testfly/unit/locator/LocatorTest.java
git commit -m "feat(locator): add first() and last() utilizing Java 21 Sequenced Collections"
```

---

### Task 5: Refactor Selector Resolution to Java 21 Switch Expressions

**Files:**

- Modify: `src/main/java/io/testfly/locator/Locator.java`
- Test: `src/test/java/io/testfly/unit/locator/LocatorTest.java`

**Interfaces:**

- Consumes: Java 21 Exhaustive Switch Expression (JEP 441)
- Produces: Type-safe, concise selector synthesis without `break` statements

- [ ] **Step 1: Refactor `buildRoot()` in `Locator.java` to Switch Expression**

Replace lines 566-595 with:

```java
    private By buildRoot() {
        return switch (kind) {
            case CSS_OR_BY -> root;
            case ROLE -> {
                String css = (semanticRole == Role.HEADING && headingLevel != null)
                        ? "h" + headingLevel + ", [role='heading'][aria-level='" + headingLevel + "']"
                        : semanticRole.cssSelector();
                yield By.cssSelector(css);
            }
            case TEXT -> By.xpath(textXPath(semanticValue, exact));
            case LABEL -> By.cssSelector(FORM_CONTROL_CSS);
            case PLACEHOLDER -> By.cssSelector(attrCss("placeholder", semanticValue, exact));
            case ALT_TEXT -> By.cssSelector(attrCss("alt", semanticValue, exact));
            case TITLE -> By.cssSelector(attrCss("title", semanticValue, exact));
            case TESTID -> By.cssSelector("[" + testIdAttribute + "='" + cssEscape(semanticValue) + "']");
            case ELEMENT -> throw new UnsupportedOperationException("Kind.ELEMENT does not have a By representation.");
        };
    }
```

- [ ] **Step 2: Run all unit tests to ensure zero regression**

Run:

```bash
mvn test -Dtest=LocatorTest
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/testfly/locator/Locator.java
git commit -m "refactor(locator): modernize buildRoot with Java 21 switch expressions"
```

---

### Task 6: Full Verification & Local Release Install

**Files:**

- Test: Entire project test suite
- Artifact: `~/.m2/repository/io/github/hakanngul/testfly/1.0.5/`

**Interfaces:**

- Consumes: All updated components
- Produces: Verified build, JaCoCo coverage, and locally installed Java 21 artifact

- [ ] **Step 1: Run full unit test suite**

Run:

```bash
mvn clean test
```

Expected: All tests pass (0 failures, 0 errors).

- [ ] **Step 2: Build and install locally with Java 21**

Run:

```bash
mvn clean install -DskipTests -Dgpg.skip=true
```

Expected: `BUILD SUCCESS` with Java 21 JAR, source JAR, and Javadoc JAR installed.

- [ ] **Step 3: Update `.agents/memories/scratchpad.md` and append to `log.md`**

Update persistent memory with Java 21 migration results.

- [ ] **Step 4: Commit**

```bash
git add .agents/
git commit -m "chore: complete Java 21 LTS migration and local deploy"
```
