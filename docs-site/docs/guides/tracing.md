---
description: "Step traces in TestFly: a self-contained HTML file per failed test with a step timeline, screenshots and an accessibility (ARIA) or DOM snapshot of every step."
id: tracing
title: Step Traces
sidebar_position: 7
---

# Step Traces

A trace is one HTML file that tells you what the page looked like at every step of a test. Open it
in any browser. It needs no server and no extra tooling. TestFly writes it when a test fails, or
for every test if you ask.

```yaml title="testfly.yml"
tracing:
  enabled: true
```

The file lands in `target/traces/{ClassName}/{testMethod}-trace.html`, and the HTML report links
to it from the test's detail panel. It works with TestNG, JUnit 5 and Cucumber.

## What a trace shows

- The **step timeline**: every step logged by `StepLogger`, and every `Locator` action and
  assertion, with the time since the test started.
- A **screenshot** for steps that were logged with one, and a final screenshot at the moment of failure.
- A **snapshot of the page at the start of each step**. Click a step to see it.
- For a failed test, the **page after the last step**, next to a **Diff** against the page before it.
- The error message and stack trace, and the video recording if recording is on.

By default the snapshot is the accessibility tree, the same text `ariaSnapshot()` prints:

```
- heading "Checkout" [level=1]
- textbox "Card number"
- button "Pay" [disabled]
```

It is small, easy to read, and does not change when the page's CSS classes or wrapper elements
do. You can copy lines from it into a
[`matchesAriaSnapshot`](/docs/guides/assertions#accessibility-assertions) template.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `tracing.enabled` | `false` | Write a trace for failed tests. |
| `tracing.captureOnPass` | `false` | Also write one for passing tests. |
| `tracing.snapshots` | `aria` | What each step records: `none`, `aria`, `dom` or `both`. |
| `tracing.snapshotMaxKb` | `256` | Longest snapshot kept per step. Longer ones are cut and end with `… truncated`. |
| `tracing.snapshotOnFailureAfter` | `true` | For a failed test, also record the page after its last step. |

`dom` records the page HTML, pruned of scripts, styles and decoration. It is bigger than `aria`,
and while it is captured the pruner briefly sets a marker attribute on page elements. Use it
when you need markup the accessibility tree does not show, such as CSS classes.

## Cost

Taking a snapshot costs one script call per step, a few milliseconds on a typical page. Nothing
is captured when `tracing.enabled` is `false`, when the test has no running browser (API and
database tests), or when the page cannot be read. A snapshot never fails a test.

Hidden content (`aria-hidden`, `display: none`) is left out of `aria` snapshots, and Shadow DOM is
not entered. See [Accessibility assertions](/docs/guides/assertions#accessibility-assertions).

## Safe to open

Page content is escaped before it is embedded, so a page that contains `</script>` or HTML
comments cannot break the trace or run code inside it.
