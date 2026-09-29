package io.testfly.agent.knowledge;

import io.testfly.agent.ActionPlan;
import io.testfly.agent.ActionStep;
import io.testfly.agent.ActionType;
import io.testfly.api.TestFlyApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Automatically learns elements and interaction patterns from AI-compiled {@link ActionPlan}s
 * and enriches the {@link PageKnowledgeStore}.
 */
@TestFlyApi(since = "1.0.5")
public final class KnowledgeLearner {

    private KnowledgeLearner() {}

    /**
     * Inspects an executed action plan and enriches the Learned Page Model for that URL.
     */
    public static void learnFromPlan(String url, ActionPlan plan) {
        if (url == null || plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return;
        }

        LearnedPageModel model = PageKnowledgeStore.getOrCreate(url, null);
        String lastHoverName = null;

        for (ActionStep step : plan.steps()) {
            if (step.locator() == null || step.locator().isBlank()) {
                continue;
            }

            String desc = step.description() != null ? step.description().toLowerCase(Locale.ROOT) : "";
            String loc = step.locator().toLowerCase(Locale.ROOT);

            if (step.action() == ActionType.HOVER) {
                String name = "hover_menu";
                List<String> synonyms = new ArrayList<>();
                if (desc.contains("profile") || loc.contains("profile") || desc.contains("account") || desc.contains("member")) {
                    name = "profile_menu";
                    synonyms.addAll(List.of("profil", "hesabım", "kullanıcı menüsü", "user profile", "account"));
                }
                LearnedElement el = new LearnedElement(name, step.locator(), ActionType.HOVER, null, synonyms);
                model.putElement(el);
                lastHoverName = name;
            } else if (step.action() == ActionType.TYPE) {
                if (desc.contains("user") || desc.contains("email") || loc.contains("user") || loc.contains("email")) {
                    LearnedElement el = new LearnedElement("username", step.locator(), ActionType.TYPE, null,
                            List.of("kullanıcı adı", "username", "e-posta", "eposta"));
                    model.putElement(el);
                } else if (desc.contains("pass") || loc.contains("pass") || desc.contains("şifre")) {
                    LearnedElement el = new LearnedElement("password", step.locator(), ActionType.TYPE, null,
                            List.of("şifre", "password", "parola"));
                    model.putElement(el);
                }
            } else if (step.action() == ActionType.CLICK) {
                if (desc.contains("login") || desc.contains("submit") || loc.contains("login") || loc.contains("submit")) {
                    LearnedElement el = new LearnedElement("submit_button", step.locator(), ActionType.CLICK, null,
                            List.of("giriş yap", "login", "giriş", "submit"));
                    model.putElement(el);
                } else if (desc.contains("bilgilerim") || loc.contains("bilgilerim")) {
                    LearnedElement el = new LearnedElement("bilgilerim", step.locator(), ActionType.CLICK, lastHoverName,
                            List.of("bilgilerim", "kişisel bilgiler", "hesap bilgileri", "bilgiler"));
                    model.putElement(el);
                }
            }
        }

        PageKnowledgeStore.save();
    }

    /**
     * Seeds or enriches the PageKnowledgeStore from all existing cached action plans.
     */
    public static void learnFromActionCache() {
        java.util.Map<String, ActionPlan> cache = io.testfly.agent.ActionCache.getAll();
        if (cache == null || cache.isEmpty()) return;
        for (ActionPlan plan : cache.values()) {
            if (plan != null && plan.urlPattern() != null && !plan.urlPattern().isBlank()) {
                learnFromPlan(plan.urlPattern(), plan);
            }
        }
    }
}
