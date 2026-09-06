package io.testfly.examples.cucumber;

import io.cucumber.testng.CucumberOptions;
import io.testfly.cucumber.BaseCucumberTest;
import org.testng.annotations.DataProvider;

/**
 * Cucumber test runner for Agentic Testing scenarios in {@code agentic_saucedemo.feature}.
 *
 * <p>Run explicitly with:
 * <pre>
 * export AI_API_KEY="your-api-key"
 * mvn test -Dtest=io.testfly.examples.cucumber.SauceDemoAgenticCucumberRunner
 * </pre>
 */
@CucumberOptions(
        features = "src/test/resources/features/agentic_saucedemo.feature",
        glue = { "io.testfly.examples.cucumber.steps", "io.testfly.cucumber" },
        plugin = {
                "pretty",
                "io.testfly.cucumber.CucumberStepLogger",
                "json:target/cucumber-agentic-report.json"
        }
)
public class SauceDemoAgenticCucumberRunner extends BaseCucumberTest {

    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
