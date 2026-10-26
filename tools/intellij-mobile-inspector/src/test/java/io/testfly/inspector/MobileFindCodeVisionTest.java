package io.testfly.inspector;

import com.intellij.codeInsight.codeVision.CodeVisionEntry;
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry;
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import kotlin.Pair;

import java.util.ArrayList;
import java.util.List;

/** The provider in a running IntelliJ platform: registered from plugin.xml, entries over the locators, clicks reveal. */
public class MobileFindCodeVisionTest extends BasePlatformTestCase {

    public void testRegisteredAndShowsCountsAboveLocators() {
        assertTrue("plugin.xml registers the provider", com.intellij.openapi.extensions.ExtensionPointName.<DaemonBoundCodeVisionProvider>create(DaemonBoundCodeVisionProvider.EP_NAME).getExtensionList().stream()
                .anyMatch(p -> p instanceof MobileFindCodeVision));

        myFixture.configureByText("LoginPage.java", """
                import io.testfly.mobile.MobileFind;
                class LoginPage extends MobilePage {
                    @MobileFind(android = "btn", ios = "okButton")
                    private MobileLocator ok;
                    @MobileFind("text=Cancel")
                    private MobileLocator cancel;
                }""");
        MobileFindCodeVision provider = new MobileFindCodeVision();
        assertTrue("no inspector open: nothing", provider.computeForEditor(myFixture.getEditor(), myFixture.getFile()).isEmpty());

        List<String> revealed = new ArrayList<>();
        LocatorCounts.Target target = new LocatorCounts.Target() {
            public String label() { return "Pixel 8"; }
            public String platform() { return "android"; }
            public void resolve(long id, List<String> selectors) {
                LocatorCounts.resolved(id, selectors.stream().map(s -> new LocatorCounts.Count(s.equals("btn") ? 1 : 0, "")).toList());
            }
            public void reveal(String selector) { revealed.add(selector); }
        };
        LocatorCounts.screenRead(target);
        try {
            // the first pass asks the page; the answer restarts the pass, which then shows the counts
            List<Pair<TextRange, CodeVisionEntry>> first = provider.computeForEditor(myFixture.getEditor(), myFixture.getFile());
            assertEquals("… counting on Pixel 8", ((ClickableTextCodeVisionEntry) first.get(0).getSecond()).getText());
            List<Pair<TextRange, CodeVisionEntry>> entries = provider.computeForEditor(myFixture.getEditor(), myFixture.getFile());
            List<String> texts = entries.stream().map(p -> ((ClickableTextCodeVisionEntry) p.getSecond()).getText()).toList();
            assertEquals(List.of("✓ 1 match on Pixel 8", "✗ no match on Pixel 8"), texts);
            String text = myFixture.getEditor().getDocument().getText();
            assertTrue(entries.get(0).getFirst().substring(text).startsWith("@MobileFind(android"));
            ((ClickableTextCodeVisionEntry) entries.get(1).getSecond()).getOnClick().invoke(null, myFixture.getEditor());
            assertEquals(List.of("text=Cancel"), revealed);
        } finally {
            LocatorCounts.closed(target);
        }
    }
}
