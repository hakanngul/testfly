---
id: reports
title: Failure Reports
sidebar_position: 8
description: "What a failed mobile test leaves behind: report steps, device screenshot, view tree text, screen recording and crash reports, and the settings that control them."
---

# Failure Reports

A failed mobile test should be explainable without running it again. TestFly logs every action as a step, and when a test fails it saves what the device showed, which elements were on the screen, a video and any app crash.

## Steps

Every locator action is a report step, named after the action and the query: `Tap getByRole(button, name="Sign in")`, `Fill getByTestId("username")`. Typed text is never logged. Failures from actions and assertions name the last reason and list the elements that were on the screen:

```
tap on getByText("Sign in") timed out after 5000 ms: the element is disabled.
  - android.widget.Button id="com.example:id/sign_in" text="Sign in" disabled [40,580 1000x120]
```

## What is saved when a test fails

| Artifact | File | Setting (default) |
|---|---|---|
| Device screenshot, also embedded in the HTML report | `target/reports/screenshots/<test>_mobile_<time>.png` | `mobile.screenshotOnFailure` (`true`) |
| View tree as text | `target/reports/screenshots/<test>_mobile_<time>.txt` | `mobile.viewTreeOnFailure` (`true`) |
| Screen recording | `target/reports/videos/<test>_mobile_<time>.mp4` | `mobile.recordVideo` (`off`) |
| Crash reports written during the test | `target/reports/crashes/<test>_<crash id>.txt` | always |

Each saved file is logged as a step of the failed test with its absolute path. Capturing never replaces the original failure: when the device cannot answer (it crashed, the server is gone), a warning is logged and the test fails with its own error.

These work the same on TestNG, JUnit 5 and Cucumber.

## View tree {#view-tree}

The `.txt` file is mobilecli's text snapshot of the screen at the moment of the failure, one indented line per element:

```
@e1 [Window]
  @e2 [NavigationBar] "Sign in"
  @e3 [TextField] "Username"
  @e4 [SecureTextField] "Password"
  @e5 [Button] "Sign in"
```

It answers questions a screenshot cannot: which element ids and labels were there, whether an alert or the keyboard covered the app. It is also small, readable input for an AI failure analysis.

Read the same text during a test with `device().viewTreeText()`.

## Videos {#videos}

`mobile.recordVideo: failures` records every mobile test and keeps the video of a failed one; `always` keeps every video. See [Screen recording](configuration#screen-recording) for details and limits.

## Crash reports {#crash-reports}

When the app crashes during a test, the next step fails with a timeout. TestFly then saves the crash reports written since the device opened, so the cause (the stack trace of the crash) is next to the failure. Older crash reports on the device are not copied. List and read them yourself with `device().crashReports()` and `device().crashReport(id)`.

## Turning artifacts off

```yaml
mobile:
  screenshotOnFailure: false
  viewTreeOnFailure: false
  recordVideo: off
```

Turn them off only for very large suites where disk space matters; they cost a few hundred milliseconds per failed test.
