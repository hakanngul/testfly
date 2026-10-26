package io.testfly.inspector;

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind;
import com.intellij.codeInsight.codeVision.CodeVisionEntry;
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering;
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry;
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import kotlin.Pair;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * While the Mobile Inspector shows a screen, a line above each {@code @MobileFind} field and
 * {@code screen().getBy…} call says how many elements it matches there; a click shows them in the inspector.
 */
public final class MobileFindCodeVision implements DaemonBoundCodeVisionProvider {

    static final String ID = "testfly.mobileFind.matches";

    @Override
    public @NotNull List<Pair<TextRange, CodeVisionEntry>> computeForEditor(@NotNull Editor editor, @NotNull PsiFile file) {
        LocatorCounts.Target target = LocatorCounts.target();
        String name = file.getName();
        if (target == null || !(name.endsWith(".java") || name.endsWith(".kt"))) {
            return List.of();
        }
        List<LocatorParser.Found> found = LocatorParser.find(editor.getDocument().getText());
        if (found.isEmpty()) {
            return List.of();
        }
        String platform = target.platform();
        List<String> selectors = new ArrayList<>();
        for (LocatorParser.Found f : found) {
            String s = f.selectorFor(platform);
            if (s != null) {
                selectors.add(s);
            }
        }
        Map<String, LocatorCounts.Count> counts = LocatorCounts.counts(selectors);
        List<Pair<TextRange, CodeVisionEntry>> out = new ArrayList<>();
        for (LocatorParser.Found f : found) {
            String selector = f.selectorFor(platform);
            String text = LocatorCounts.label(selector == null ? null : counts.get(selector), platform, target.label(), selector != null);
            String tooltip = f.source() + (selector != null ? "\nSelector on " + platform + ": " + selector + "\nClick to show the matches in the Mobile Inspector" : "");
            CodeVisionEntry entry = new ClickableTextCodeVisionEntry(text, ID, (event, ed) -> {
                if (selector != null) {
                    target.reveal(selector);
                }
                return Unit.INSTANCE;
            }, null, text, tooltip, List.of());
            out.add(new Pair<>(new TextRange(f.start(), f.end()), entry));
        }
        return out;
    }

    @Override
    public @NotNull String getName() {
        return "TestFly selector matches";
    }

    @Override
    public @NotNull List<CodeVisionRelativeOrdering> getRelativeOrderings() {
        return List.of();
    }

    @Override
    public @NotNull CodeVisionAnchorKind getDefaultAnchor() {
        return CodeVisionAnchorKind.Top;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }
}
