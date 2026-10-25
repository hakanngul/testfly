---
id: actions
title: Actions and Reads
sidebar_position: 4
description: "Auto-waiting mobile actions (tap, fill, check, swipe, pinch, drag, scrollIntoView), raw coordinate gestures, element reads and explicit waits."
---

# Actions and Reads

## How an action waits {#auto-waiting}

Every locator action reads the screen again and again (every `timeouts.pollMs`, default 100 ms) until the element is **actionable**, then acts on the centre of its bounds:

1. exactly one element matches ([strict mode](locators#strict-mode));
2. it is visible;
3. it is enabled;
4. it is stable: its bounds did not change between two reads (no animation in progress).

When the element is not actionable within `timeouts.actionMs` (default 5000 ms) the action fails with a `MobileTimeoutException` that names the last reason and lists the elements seen:

```
tap on getByText("Sign in") timed out after 5000 ms: the element is disabled.
  - android.widget.Button id="com.example:id/sign_in" text="Sign in" disabled [40,580 1000x120]
```

Every request inside the action is bounded by the time left of that budget, so a stalled mobilecli server cannot hold a test longer than the budget. `locator.withTimeout(Duration)` gives one locator a different budget; `mobile.timeouts.actionMs` changes the default ([Configuration](configuration)).

Actions are logged as report steps ("Tap getByText("Sign in")"). Typed text is not logged, so passwords stay out of reports.

## Taps and text

| Method | What it does |
|---|---|
| `tap()` | taps the centre |
| `doubleTap()` | two taps |
| `longPress()`, `longPress(Duration.ofSeconds(2))` | long press (mobilecli's default duration, or the given one) |
| `fill("ada")` | taps the field, clears it, types the text |
| `type("ada")` | same as `fill` (named like the web `Locator.type`) |
| `append(" Lovelace")` | taps the field and types after its current content |
| `clear()` | taps the field and clears it |

```java
screen().getByTestId("username").fill(System.getenv("MOBILE_LOGIN_USER"));
screen().getByTestId("password").fill(System.getenv("MOBILE_LOGIN_PASSWORD"));
screen().getByRole(MobileRole.BUTTON, "Sign in").tap();
```

The keyboard can stay open after typing and cover elements below the field: `device().hideKeyboard()` closes it ([Device](device#keyboard)).

## Checkboxes, radios and switches

| Method | What it does |
|---|---|
| `check()` | taps when the element is not checked yet |
| `uncheck()` | taps when the element is checked |
| `setChecked(boolean)` | `check()` or `uncheck()` by value |

```java
screen().getByText("Remember me").check();            // safe to call twice
screen().getByRole(MobileRole.SWITCH, "Wi-Fi").setChecked(false);
```

They read the element's `checked` state from the view tree. They do not verify the new state afterwards; add `expect(locator).toBeChecked()` when the tap may be ignored by the app.

## Gestures on an element

| Method | What it does |
|---|---|
| `swipe(SwipeDirection.LEFT)` | swipes from the element's centre, half its size or at least 200 px (carousels, swipe-to-delete rows) |
| `pinchOut()` / `pinchIn()` | two-finger zoom in / out centred on the element (maps, images) |
| `dragTo(target)` | presses the element, drags it onto the centre of `target` and releases (reordering, sliders) |
| `scrollIntoView()` | swipes the screen up until exactly one match is visible |
| `scrollIntoView(SwipeDirection.DOWN)` | the same in another direction |

`scrollIntoView` stops with a `MobileTimeoutException` when the screen no longer changes (end of the list) or the timeout runs out.

```java
screen().getByText("Privacy").scrollIntoView();
screen().getByText("Privacy").tap();
screen().getByTestId("row-3").swipe(SwipeDirection.LEFT);
screen().getByRole(MobileRole.BUTTON, "Delete").tap();
```

## Screen gestures and coordinates

`screen()` has gestures that do not need an element:

| Method | What it does |
|---|---|
| `screen().swipe(SwipeDirection.UP)` | swipes across the middle of the screen (half its height or width) |
| `screen().tap(x, y)` | taps a point, in screen points (the units of the view tree) |
| `screen().doubleTap(x, y)` | two taps at a point |
| `screen().longPress(x, y)`, `longPress(x, y, Duration)` | long press at a point |

Coordinates depend on the device size and layout; prefer locators and keep coordinates for canvases, maps and games. They do not wait or retry.

## Reads {#reads}

Reads that **wait** for one visible element:

| Method | Returns |
|---|---|
| `getText()` | the displayed text: `text`, else `label`, else `value` |
| `getValue()` | the `value` (text field content, slider position); `""` when it has none |
| `getAttribute(name)` | any attribute (`name`, `label`, `value`, `text`, `identifier`, `type`, `enabled`, … or a raw mobilecli field); `resource-id`, `content-desc` and `class` work as aliases; `null` when missing |
| `bounds()` | `MobileBounds(x, y, width, height)` in screen points |
| `screenshot()` | a PNG of just the element (cropped by mobilecli) |
| `node()` | the element as a `MobileNode` snapshot (all fields, children) |

Reads of **the current screen**, without waiting:

| Method | Returns |
|---|---|
| `count()` | number of matches |
| `all()` | one locator per current match, e.g. to loop over list rows |
| `nodes()` | the matches as `MobileNode`s |
| `allTexts()` | the displayed text of every match |
| `isPresent()` | at least one match exists |
| `isVisible()`, `isHidden()` | exactly one visible match / no visible match |
| `isEnabled()`, `isDisabled()`, `isSelected()`, `isChecked()`, `isFocused()` | the state of the one match (strict) |

```java
for (MobileLocator row : screen().getByRole(MobileRole.LISTITEM).all()) {
    System.out.println(row.getText());
}
Files.write(Path.of("target/logo.png"), screen().getByTestId("logo").screenshot());
```

A non-waiting read sees the screen as it is at that moment, possibly mid-animation. To check a state, prefer an [assertion](assertions), which retries.

## Explicit waits

| Method | Waits until |
|---|---|
| `waitForVisible()` | exactly one match is visible |
| `waitForHidden()` | no match is visible (or nothing matches) |
| `waitForEnabled()` | the one match is enabled |
| `waitForDisabled()` | the one match is disabled |

Actions already wait; explicit waits are for screens you only want to reach, for example a spinner that must go away before the next read:

```java
screen().getByRole(MobileRole.PROGRESSBAR).waitForHidden();
```

## Hardware buttons and system UI

`device().pressButton("BACK")` presses a hardware button: `HOME`, `BACK` (Android), `ENTER`, `VOLUME_UP`, `VOLUME_DOWN`, `POWER`, and on Android `APP_SWITCH` and the `DPAD_*` keys. See [Device](device) for apps, deep links, orientation and the keyboard.

System alerts (iOS permission and tracking prompts, Android runtime permissions) can be answered automatically with `mobile.alerts: accept | dismiss` ([Configuration](configuration)).
