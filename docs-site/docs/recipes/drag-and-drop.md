---
description: "Drag and drop in Selenium using the Actions API: drag one element onto another, drag by offset, and fall back to HTML5 drag events when the standard API doesn't trigger the app."
id: drag-and-drop
title: Drag and drop
sidebar_label: Drag & drop
---

# Drag and drop

HTML5 drag-and-drop is one of the most implementation-dependent interactions in Selenium. Some apps respond to the standard `Actions.dragAndDrop()`, others only fire on HTML5 `dragstart`/`drop` events. TestFly does not hide this complexity — it exposes raw Selenium's `Actions` class through `BaseTest` / `BasePage` so you can choose the approach that matches your app.

---

## Standard Actions drag-and-drop

Start here. It works for most native HTML5 draggable implementations:

```java title="BoardTest.java"
import io.testfly.test.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.testng.annotations.Test;

public class BoardTest extends BaseTest {

    @Test
    public void movesCardToDone() {
        open("/board");

        WebElement card = find("#card-42").first();
        WebElement doneColumn = find("#done").first();

        new Actions(getDriver())
            .dragAndDrop(card, doneColumn)
            .build()
            .perform();

        assertThat(find("#done #card-42")).isVisible();
    }
}
```

---

## Drag by offset

Use this when the drop target is a coordinate region rather than an element, or when you need to drag part-way:

```java
WebElement card = find("#card-42").first();

new Actions(getDriver())
    .clickAndHold(card)
    .moveByOffset(300, 0)   // drag 300px to the right
    .release()
    .build()
    .perform();
```

---

## HTML5 event fallback

If `dragAndDrop()` does not trigger the app's event handlers, fire the HTML5 events directly with JavaScript:

```java title="BoardPage.java"
import io.testfly.test.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;

public class BoardPage extends BasePage {

    public void dragAndDropJs(By sourceLocator, By targetLocator) {
        WebElement source = find(sourceLocator).first();
        WebElement target = find(targetLocator).first();

        String script =
            "function createEvent(typeOfEvent) {" +
            "  var event = document.createEvent('CustomEvent');" +
            "  event.initCustomEvent(typeOfEvent, true, true, null);" +
            "  event.dataTransfer = { data: {}, setData: function(key, value) { this.data[key] = value; }, getData: function(key) { return this.data[key]; } };" +
            "  return event;" +
            "}" +
            "function dispatchEvent(element, event, transferData) {" +
            "  if (transferData !== undefined) { event.dataTransfer = transferData; }" +
            "  if (element.dispatchEvent) { element.dispatchEvent(event); }" +
            "}" +
            "function simulateHTML5DragAndDrop(element, destination) {" +
            "  var dragStartEvent = createEvent('dragstart');" +
            "  dispatchEvent(element, dragStartEvent);" +
            "  var dropEvent = createEvent('drop');" +
            "  dispatchEvent(destination, dropEvent, dragStartEvent.dataTransfer);" +
            "  var dragEndEvent = createEvent('dragend');" +
            "  dispatchEvent(element, dragEndEvent, dropEvent.dataTransfer);" +
            "}" +
            "simulateHTML5DragAndDrop(arguments[0], arguments[1]);";

        ((JavascriptExecutor) getDriver()).executeScript(script, source, target);
    }
}
```

```java title="BoardTest.java"
public class BoardTest extends BaseTest {

    @Test
    public void movesCardWithJsFallback() {
        open("/board");
        BoardPage board = new BoardPage();
        board.dragAndDropJs(By.id("card-42"), By.id("done"));
        assertThat(find("#done #card-42")).isVisible();
    }
}
```

---

## When to use which

| Approach | Use when |
|---|---|
| `Actions.dragAndDrop` | Standard HTML5 draggable elements; app uses native events |
| `clickAndHold + moveByOffset` | Drop target is a region, or you need partial drag |
| JavaScript HTML5 events | App ignores native mouse events and listens only to `dragstart`/`drop` |

---

## Common pitfalls

- **Draggable element not visible.** Scroll it into view first with `scrollTo(By)`.
- **Drop target covered by another element.** Use `jsClick` or adjust coordinates.
- **Flaky coordinates across viewports.** Pin the browser window size in CI with `browser.arguments: [--window-size=1920,1080]`.
- **Event not firing.** Try the JavaScript fallback before adding sleeps.

---

**Deeper reference:** [BasePage](/docs/guides/base-page) — `scrollTo()`, `jsClick()`, and other helpers for tricky interactions.
