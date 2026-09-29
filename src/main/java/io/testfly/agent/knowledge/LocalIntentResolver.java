package io.testfly.agent.knowledge;

import io.testfly.agent.ActionPlan;
import io.testfly.agent.ActionStep;
import io.testfly.agent.ActionType;
import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves natural language goals into executable {@link ActionPlan}s locally using
 * the Learned Page Model (Auto-POM), completely bypassing the LLM when possible.
 */
@TestFlyApi(since = "1.0.5")
public final class LocalIntentResolver {

    private static final Pattern QUOTED_STRING = Pattern.compile("['\"]([^'\"]+)['\"]");

    private LocalIntentResolver() {}

    /**
     * Attempts to resolve a goal using the learned page model for the given URL.
     *
     * @param url  the current page URL
     * @param goal the natural language goal
     * @return a compiled ActionPlan if resolvable locally, or {@code null} if AI fallback is required
     */
    public static ActionPlan resolve(String url, String goal) {
        if (url == null || goal == null || goal.isBlank()) {
            return null;
        }

        Optional<LearnedPageModel> modelOpt = PageKnowledgeStore.getPageModel(url);
        if (modelOpt.isEmpty()) {
            return null;
        }

        LearnedPageModel model = modelOpt.get();

        // 1. Try multi-input form submission (e.g. Login form)
        ActionPlan formPlan = tryResolveFormInput(model, url, goal);
        if (formPlan != null) {
            return formPlan;
        }

        // 2. Try single element navigation/click/hover
        ActionPlan singleElementPlan = tryResolveSingleElement(model, url, goal);
        if (singleElementPlan != null) {
            return singleElementPlan;
        }

        return null;
    }

    private static ActionPlan tryResolveFormInput(LearnedPageModel model, String url, String goal) {
        String lowerGoal = goal.toLowerCase();
        List<String> quotedValues = extractQuotedValues(goal);

        // Check for username + password login intent
        boolean hasUsernameKeyword = lowerGoal.contains("user") || lowerGoal.contains("kullanıcı") || lowerGoal.contains("email") || lowerGoal.contains("posta");
        boolean hasPasswordKeyword = lowerGoal.contains("pass") || lowerGoal.contains("şifre") || lowerGoal.contains("parola");

        if (hasUsernameKeyword && hasPasswordKeyword && quotedValues.size() >= 2) {
            Optional<LearnedElement> userInput = model.findElement("username");
            Optional<LearnedElement> passInput = model.findElement("password");
            Optional<LearnedElement> submitBtn = model.findElement("submit_button")
                    .or(() -> model.findElement("login_button"))
                    .or(() -> model.findElement("login"));

            if (userInput.isPresent() && passInput.isPresent() && submitBtn.isPresent()) {
                List<ActionStep> steps = new ArrayList<>();
                steps.add(new ActionStep(ActionType.TYPE, userInput.get().locator(), quotedValues.get(0),
                        "Type username into " + userInput.get().name()));
                steps.add(new ActionStep(ActionType.TYPE, passInput.get().locator(), quotedValues.get(1),
                        "Type password into " + passInput.get().name()));
                steps.add(new ActionStep(ActionType.CLICK, submitBtn.get().locator(), null,
                        "Click " + submitBtn.get().name()));
                return new ActionPlan(goal, url, steps, System.currentTimeMillis());
            }
        }

        return null;
    }

    private static ActionPlan tryResolveSingleElement(LearnedPageModel model, String url, String goal) {
        List<LearnedElement> matched = new ArrayList<>();
        for (LearnedElement el : model.elements().values()) {
            if (el.matches(goal)) {
                matched.add(el);
            }
        }

        if (matched.isEmpty()) {
            return null;
        }

        // Prioritize child elements with parent triggers over parent hover containers
        matched.sort((a, b) -> {
            boolean aHasParent = a.parentTrigger() != null && !a.parentTrigger().isBlank();
            boolean bHasParent = b.parentTrigger() != null && !b.parentTrigger().isBlank();
            if (aHasParent != bHasParent) {
                return aHasParent ? -1 : 1;
            }
            if (a.actionType() == ActionType.CLICK && b.actionType() == ActionType.HOVER) {
                return -1;
            }
            if (b.actionType() == ActionType.CLICK && a.actionType() == ActionType.HOVER) {
                return 1;
            }
            return 0;
        });

        LearnedElement best = matched.get(0);
        List<ActionStep> steps = new ArrayList<>();

        // If element requires a parent hover (e.g. dropdown menu)
        if (best.parentTrigger() != null && !best.parentTrigger().isBlank()) {
            Optional<LearnedElement> parent = model.findElement(best.parentTrigger());
            parent.ifPresent(p -> steps.add(new ActionStep(
                    ActionType.HOVER,
                    p.locator(),
                    null,
                    "Hover over " + p.name() + " trigger"
            )));
        }

        // Main action
        ActionType type = best.actionType() != null ? best.actionType() : ActionType.CLICK;
        steps.add(new ActionStep(type, best.locator(), null, "Execute " + best.name() + " on " + model.pageName()));
        return new ActionPlan(goal, url, steps, System.currentTimeMillis());
    }

    private static List<String> extractQuotedValues(String text) {
        List<String> values = new ArrayList<>();
        Matcher m = QUOTED_STRING.matcher(text);
        while (m.find()) {
            values.add(m.group(1));
        }
        return values;
    }
}
