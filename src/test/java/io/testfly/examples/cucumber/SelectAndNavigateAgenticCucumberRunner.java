package io.testfly.examples.cucumber;

import io.cucumber.testng.CucumberOptions;
import io.testfly.cucumber.BaseCucumberTest;
import org.testng.annotations.DataProvider;

/**
 * Cucumber runner for the SELECT and NAVIGATE scenarios in
 * {@code agentic_select_navigate.feature}.
 *
 * <p>Needs no new step definitions: the generic agentic steps already declared in
 * {@link io.testfly.examples.cucumber.steps.SauceDemoSteps} carry both primitives, because
 * {@code act(goal)} compiles whatever action types the goal implies.
 *
 * <p>Run explicitly with:
 * <pre>
 * export AI_API_KEY="your-api-key"
 * mvn test -Pexamples -Dtest=io.testfly.examples.cucumber.SelectAndNavigateAgenticCucumberRunner
 * </pre>
 */
@CucumberOptions(
        features = "src/test/resources/features/agentic_select_navigate.feature",
        glue = { "io.testfly.examples.cucumber.steps", "io.testfly.cucumber" },
        plugin = {
                "pretty",
                "io.testfly.cucumber.CucumberStepLogger",
                "json:target/cucumber-select-navigate-report.json"
        }
)
public class SelectAndNavigateAgenticCucumberRunner extends BaseCucumberTest {

    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
