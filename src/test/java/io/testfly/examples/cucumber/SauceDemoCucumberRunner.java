package io.testfly.examples.cucumber;

import org.testng.annotations.DataProvider;

import io.cucumber.testng.CucumberOptions;
import io.testfly.cucumber.BaseCucumberTest;

/**
 * Cucumber runner for the Sauce Demo BDD example.
 *
 * <p>
 * Run explicitly with:
 * 
 * <pre>
 * mvn test -Dtest=io.testfly.examples.cucumber.SauceDemoCucumberRunner
 * </pre>
 */
@CucumberOptions(features = "src/test/resources/features/", glue = {
                "io.testfly.examples.cucumber.steps", "io.testfly.cucumber" }, plugin = {
                                "pretty",
                                "io.testfly.cucumber.CucumberStepLogger",
                                "json:target/cucumber-report.json",
                })
public class SauceDemoCucumberRunner extends BaseCucumberTest {

        @Override
        @DataProvider(parallel = true)
        public Object[][] scenarios() {
                return super.scenarios();
        }
}
