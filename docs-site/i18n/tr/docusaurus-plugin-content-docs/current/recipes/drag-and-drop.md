---
description: "Selenium'da Actions API'siyle sürükle-bırak: bir öğeyi başka bir öğenin üzerine sürükleyin, uzaklığa göre sürükleyin ve standart API uygulamayı tetiklemediğinde HTML5 sürükleme olaylarına geri dönün."
id: drag-and-drop
title: Sürükle ve bırak
sidebar_label: Drag & drop
---

# Sürükle ve bırak

HTML5 sürükle-bırak, Selenium'daki uygulamaya en bağımlı etkileşimlerden biridir. Bazı uygulamalar standart `Actions.dragAndDrop()` yöntemine yanıt verirken, diğerleri yalnızca HTML5 `dragstart`/`drop` olaylarında tetiklenir. TestFly bu karmaşıklığı gizlemez — uygulamanıza uygun yaklaşımı seçebilmeniz için ham Selenium `Actions` sınıfını `BaseTest` / `BasePage` üzerinden sunar.

---

## Standart Actions sürükle-bırak

Buradan başlayın. Çoğu doğal HTML5 sürüklenebilir uygulamasıyla çalışır:

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

## Uzaklığa göre sürükleme

Bırakma hedefi bir öğe değil de bir koordinat bölgesi olduğunda veya ortada bir yere kadar sürüklemeniz gerektiğinde bunu kullanın:

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

## HTML5 olay geri dönüşü

`dragAndDrop()` uygulamanın olay işleyicilerini tetiklemezse, HTML5 olaylarını doğrudan JavaScript ile tetikleyin:

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

## Hangisini ne zaman kullanmalı

| Yaklaşım | Ne zaman kullanılır |
|---|---|
| `Actions.dragAndDrop` | Standart HTML5 sürüklenebilir öğeler; uygulama doğal olayları kullanıyor |
| `clickAndHold + moveByOffset` | Bırakma hedefi bir bölge veya kısmi sürükleme gerekiyor |
| JavaScript HTML5 olayları | Uygulama doğal fare olaylarını yok sayar ve yalnızca `dragstart`/`drop` olaylarını dinler |

---

## Sık karşılaşılan tuzaklar

- **Sürüklenebilir öğe görünür değil.** Önce `scrollTo(By)` ile onu görünür alana getirin.
- **Bırakma hedefi başka bir öğe tarafından kaplanmış.** `jsClick` kullanın veya koordinatları ayarlayın.
- **Görüntü alanları arasında değişken koordinatlar.** CI'da `browser.arguments: [--window-size=1920,1080]` ile tarayıcı penceresi boyutunu sabitleyin.
- **Olay tetiklenmiyor.** Bekleme eklemeden önce JavaScript geri dönüşünü deneyin.

---

**Daha derin referans:** [BasePage](/docs/guides/base-page) — zorlu etkileşimler için `scrollTo()`, `jsClick()` ve diğer yardımcılar.