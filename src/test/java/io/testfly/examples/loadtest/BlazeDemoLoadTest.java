package io.testfly.examples.loadtest;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import io.testfly.loadtest.LoadTestFeeder;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Load test for the BlazeDemo flight booking flow at https://blazedemo.com.
 *
 * <p>
 * Defaults (base URL, users, ramp-up, hold, engine) come from
 * {@code src/test/resources/testfly-blazedemo.yml}; the annotations and the
 * fluent API below override them per test. The class has no base URL of its
 * own, so run it with {@code -Dtestfly.profile=blazedemo}.
 *
 * <pre>
 * mvn test -Pexamples -Dtestfly.profile=blazedemo -Dtest=BlazeDemoLoadTest
 * </pre>
 *
 * <h3>Booking flow</h3>
 * <ol>
 * <li>{@code GET /} — homepage with the departure/destination form</li>
 * <li>{@code POST /reserve.php} — list flights for a route</li>
 * <li>{@code POST /purchase.php} — open the purchase form for a flight</li>
 * <li>{@code POST /confirmation.php} — submit the purchase</li>
 * </ol>
 *
 * <p>
 * Form posts are sent as URL-encoded string bodies with an explicit
 * {@code Content-Type}, because {@code LoadStep.formParam(...)} is not yet
 * applied by the engines.
 *
 * <p>
 * BlazeDemo is a public demo site: keep user counts low. The methods run one at
 * a time ({@code singleThreaded}) so surefire's {@code parallel=methods} never
 * stacks the scenarios against the site.
 */
@Test(singleThreaded = true)
public class BlazeDemoLoadTest extends BaseLoadTest {

    private static final String FORM = "application/x-www-form-urlencoded";

    /**
     * Single-endpoint smoke load on the homepage using the YAML defaults.
     */
    @Test
    public void homepageUnderLoad() {
        load("/")
                .run()
                .assertStatus(200)
                .assertP95Below(3000)
                .assertErrorRateBelow(0.05);
    }

    /**
     * Full booking flow: homepage → reserve → purchase → confirmation.
     * Each virtual user takes a different route from the CSV feeder.
     */
    @Test
    public void bookingFlowUnderLoad() {
        loadScenario("BlazeDemo Booking Flow")
                .users(10)
                .rampUp(Duration.ofSeconds(10))
                .hold(Duration.ofSeconds(30))
                .cooldown(Duration.ofSeconds(5))

                .step("Homepage")
                .get("/")
                .check(status().is(200))
                .and()

                .step("Reserve")
                .post("/reserve.php")
                .header("Content-Type", FORM)
                .body("fromPort=${fromPort}&toPort=${toPort}")
                .check(status().is(200))
                .and()

                .step("Purchase")
                .post("/purchase.php")
                .header("Content-Type", FORM)
                .body("fromPort=${fromPort}&toPort=${toPort}"
                        + "&airline=Virgin+America&flight=43&price=472.56")
                .check(status().is(200))
                .and()

                .step("Confirmation")
                .post("/confirmation.php")
                .header("Content-Type", FORM)
                .body("inputName=Load+Test+User&address=123+Main+St&city=Anytown"
                        + "&state=CA&zipCode=12345&cardType=visa"
                        + "&creditCardNumber=4111111111111111&nameOnCard=Load+Test+User")
                .check(status().is(200))
                .and()

                .feed(LoadTestFeeder.csv("loadtest/blazedemo-routes.csv"))
                .thinkTime(500, 2000)

                .run()
                .assertP95Below(3000)
                .assertErrorRateBelow(0.05)
                .assertStepP95Below("Reserve", 2000)
                .assertStepErrorRateBelow("Confirmation", 0.05)
                .assertNoStatus(500);
    }

    /**
     * Short spike on the flight search: more users, fast ramp-up.
     * Method-level {@code @LoadTest} overrides the YAML defaults.
     */
    @Test
    @LoadTest(users = 25, rampUp = "5s", hold = "20s", cooldown = "5s")
    public void flightSearchSpike() {
        loadScenario("BlazeDemo Search Spike")
                .step("Reserve")
                .post("/reserve.php")
                .header("Content-Type", FORM)
                .body("fromPort=Paris&toPort=Buenos+Aires")
                .check(status().is(200))
                .and()

                .run()
                .assertP95Below(5000)
                .assertErrorRateBelow(0.10)
                .assertThroughputAbove(2);
    }
}
