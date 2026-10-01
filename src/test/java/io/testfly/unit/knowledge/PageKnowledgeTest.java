package io.testfly.unit.knowledge;

import io.testfly.agent.ActionPlan;
import io.testfly.agent.ActionStep;
import io.testfly.agent.ActionType;
import io.testfly.agent.knowledge.KnowledgeLearner;
import io.testfly.agent.knowledge.LearnedElement;
import io.testfly.agent.knowledge.LearnedPageModel;
import io.testfly.agent.knowledge.LocalIntentResolver;
import io.testfly.agent.knowledge.PageKnowledgeStore;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class PageKnowledgeTest {

    private static final String LOGIN_URL = "https://www.stage.letsbet.de/tr/uyelik/giris";
    private static final String HOME_URL = "https://www.stage.letsbet.de/";

    @BeforeMethod
    public void setup() {
        PageKnowledgeStore.clear();
    }

    @Test
    public void learnedElement_matchesByNameAndSynonyms() {
        LearnedElement el = new LearnedElement(
                "username",
                "#userNameLP",
                ActionType.TYPE,
                null,
                List.of("kullanıcı adı", "e-posta", "eposta")
        );

        assertTrue(el.matches("username"));
        assertTrue(el.matches("kullanıcı adı"));
        assertTrue(el.matches("Lütfen kullanıcı adınızı girin"));
        assertTrue(el.matches("e-posta"));
        assertFalse(el.matches("şifre"));
    }

    @Test
    public void learnedPageModel_recordsAndFindsElements() {
        LearnedPageModel model = new LearnedPageModel("/tr/uyelik/giris", "LoginPage");
        LearnedElement username = new LearnedElement("username", "#user", ActionType.TYPE, null, List.of("kullanıcı adı"));
        LearnedElement password = new LearnedElement("password", "#pass", ActionType.TYPE, null, List.of("şifre"));

        model.putElement(username);
        model.putElement(password);

        Optional<LearnedElement> foundUser = model.findElement("kullanıcı adı");
        assertTrue(foundUser.isPresent());
        assertEquals(foundUser.get().locator(), "#user");

        Optional<LearnedElement> foundPass = model.findElement("şifre");
        assertTrue(foundPass.isPresent());
        assertEquals(foundPass.get().locator(), "#pass");

        Optional<LearnedElement> notFound = model.findElement("bilinmeyen");
        assertTrue(notFound.isEmpty());
    }

    @Test
    public void localIntentResolver_resolvesFormLoginLocally() {
        LearnedPageModel model = PageKnowledgeStore.getOrCreate(LOGIN_URL, "LoginPage");
        model.putElement(new LearnedElement("username", "#userNameLP", ActionType.TYPE, null, List.of("kullanıcı adı")));
        model.putElement(new LearnedElement("password", "#realpassLP", ActionType.TYPE, null, List.of("şifre")));
        model.putElement(new LearnedElement("login_button", "#btnLoginSubmitLP", ActionType.CLICK, null, List.of("giriş yap", "login")));

        String goal = "Enter username 'testuser' and password 'secret123', then click Login";
        ActionPlan plan = LocalIntentResolver.resolve(LOGIN_URL, goal);

        assertNotNull(plan, "LocalIntentResolver should resolve login form without AI");
        assertEquals(plan.steps().size(), 3);
        assertEquals(plan.steps().get(0).action(), ActionType.TYPE);
        assertEquals(plan.steps().get(0).locator(), "#userNameLP");
        assertEquals(plan.steps().get(0).value(), "testuser");

        assertEquals(plan.steps().get(1).action(), ActionType.TYPE);
        assertEquals(plan.steps().get(1).locator(), "#realpassLP");
        assertEquals(plan.steps().get(1).value(), "secret123");

        assertEquals(plan.steps().get(2).action(), ActionType.CLICK);
        assertEquals(plan.steps().get(2).locator(), "#btnLoginSubmitLP");
    }

    @Test
    public void localIntentResolver_resolvesHoverDropdownElement() {
        LearnedPageModel model = PageKnowledgeStore.getOrCreate(HOME_URL, "HomePage");
        model.putElement(new LearnedElement("profile_menu", "#profile-nav", ActionType.HOVER, null, List.of("profil", "user profile")));
        model.putElement(new LearnedElement("bilgilerim", "a[href='/tr/hesabim/bilgilerim']", ActionType.CLICK, "profile_menu", List.of("bilgilerim", "kişisel bilgiler")));

        String goal = "Open Bilgilerim from profile menu";
        ActionPlan plan = LocalIntentResolver.resolve(HOME_URL, goal);

        assertNotNull(plan, "LocalIntentResolver should resolve dropdown item with parent hover");
        assertEquals(plan.steps().size(), 2);
        assertEquals(plan.steps().get(0).action(), ActionType.HOVER);
        assertEquals(plan.steps().get(0).locator(), "#profile-nav");
        assertEquals(plan.steps().get(1).action(), ActionType.CLICK);
        assertEquals(plan.steps().get(1).locator(), "a[href='/tr/hesabim/bilgilerim']");
    }

    @Test
    public void knowledgeLearner_extractsAndStoresDiscoveredElements() {
        List<ActionStep> steps = List.of(
                new ActionStep(ActionType.HOVER, "#profile-nav", null, "Hover over profile menu in the header"),
                new ActionStep(ActionType.CLICK, "a[href='/tr/hesabim/bilgilerim']", null, "Click Bilgilerim link in the menu")
        );
        ActionPlan plan = new ActionPlan("Hover profile then click Bilgilerim", HOME_URL, steps, System.currentTimeMillis());

        KnowledgeLearner.learnFromPlan(HOME_URL, plan);

        Optional<LearnedElement> profile = PageKnowledgeStore.findElement(HOME_URL, "profil");
        assertTrue(profile.isPresent(), "profile_menu should be learned");
        assertEquals(profile.get().locator(), "#profile-nav");

        Optional<LearnedElement> bilgilerim = PageKnowledgeStore.findElement(HOME_URL, "bilgilerim");
        assertTrue(bilgilerim.isPresent(), "bilgilerim link should be learned");
        assertEquals(bilgilerim.get().locator(), "a[href='/tr/hesabim/bilgilerim']");
        assertEquals(bilgilerim.get().parentTrigger(), "profile_menu");
    }

    @Test
    public void learnedPageModel_serializesAndDeserializesWithJackson() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        LearnedPageModel model = new LearnedPageModel("/test-route", "TestPage");
        model.putElement(new LearnedElement("submit_btn", "#submit", ActionType.CLICK, null, List.of("gönder", "submit")));

        String json = mapper.writeValueAsString(model);
        assertTrue(json.contains("\"elements\""), "JSON should contain 'elements' property");
        assertTrue(json.contains("\"submit_btn\""), "JSON should contain 'submit_btn' element key");
        assertTrue(json.contains("#submit"), "JSON should contain '#submit' locator");

        LearnedPageModel deserialized = mapper.readValue(json, LearnedPageModel.class);
        assertEquals(deserialized.urlPattern(), "/test-route");
        assertEquals(deserialized.pageName(), "TestPage");
        assertNotNull(deserialized.elements().get("submit_btn"));
        assertEquals(deserialized.elements().get("submit_btn").locator(), "#submit");
    }
}
