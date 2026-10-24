package io.testfly.examples.cucumber.mobile;

import io.cucumber.testng.CucumberOptions;
import io.testfly.cucumber.BaseCucumberTest;

/**
 * Cucumber runner for the native iOS login example.
 *
 * <pre>
 * mobilecli server start                      # keep it running
 * export MOBILE_LOGIN_USER=... MOBILE_LOGIN_PASSWORD=...
 * mvn test -Dtestfly.profile=mobile-login -Dtest=IosLoginCucumberRunner
 * </pre>
 *
 * The glue is limited to this package plus the framework hooks, so the web Cucumber runners never
 * load these steps.
 */
@CucumberOptions(features = "src/test/resources/features-mobile/", glue = {
        "io.testfly.examples.cucumber.mobile", "io.testfly.cucumber" }, plugin = {
                "pretty",
                "io.testfly.cucumber.CucumberStepLogger",
                "json:target/cucumber-mobile-report.json",
        })
public class IosLoginCucumberRunner extends BaseCucumberTest {
}
