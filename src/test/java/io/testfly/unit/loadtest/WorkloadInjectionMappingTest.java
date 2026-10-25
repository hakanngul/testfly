package io.testfly.unit.loadtest;

import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import io.testfly.loadtest.internal.GatlingBridge;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.*;
import static org.testng.Assert.*;

/**
 * Maps a serialised workload to Gatling injection steps ({@code TestFlyGatlingSimulation.openInjection} and
 * {@code closedInjection}) for every step type. The oracle is Gatling's own DSL called with the expected
 * arguments: both sides are compared through {@code asScala()}, so type and values must match exactly.
 */
public class WorkloadInjectionMappingTest {

    @BeforeClass
    public void requireGatling() {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not on the classpath");
        }
    }

    @Test
    public void openInjection_mapsEveryStepType_rateZeroToPause_andFloorsDurationsAtOneMillisecond()
            throws Exception {
        Workload w = Workload.open(
                OpenStep.atOnce(3),
                OpenStep.ramp(4, Duration.ofSeconds(2)),
                OpenStep.constantRate(10, Duration.ofMillis(1500)),
                OpenStep.constantRate(0, Duration.ofSeconds(1)),
                OpenStep.rampRate(2, 6, Duration.ofSeconds(2)),
                OpenStep.rampRate(0, 0, Duration.ofMillis(700)),
                OpenStep.rampRate(0, 3, Duration.ofSeconds(1)),
                OpenStep.stressPeak(4, Duration.ofSeconds(2)),
                OpenStep.nothingFor(Duration.ofMillis(250)),
                OpenStep.ramp(1, Duration.ofNanos(1)));

        @SuppressWarnings("unchecked")
        List<OpenInjectionStep> actual = (List<OpenInjectionStep>) inject("openInjection", w);

        List<OpenInjectionStep> expected = List.of(
                atOnceUsers(3),
                rampUsers(4).during(Duration.ofSeconds(2)),
                constantUsersPerSec(10).during(Duration.ofMillis(1500)),
                nothingFor(Duration.ofSeconds(1)),               // rate 0 starts nobody: a pause
                rampUsersPerSec(2).to(6).during(Duration.ofSeconds(2)),
                nothingFor(Duration.ofMillis(700)),               // rates 0 -> 0: a pause
                rampUsersPerSec(0).to(3).during(Duration.ofSeconds(1)),
                stressPeakUsers(4).during(Duration.ofSeconds(2)),
                nothingFor(Duration.ofMillis(250)),
                rampUsers(1).during(Duration.ofMillis(1)));      // 1 ns rounds up to the 1 ms floor
        assertEquals(actual.size(), expected.size(), "one Gatling step per workload step: " + scala(actual));
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(scala(actual.get(i)), scala(expected.get(i)), "step " + i);
        }
    }

    @Test
    public void closedInjection_mapsEveryStepType() throws Exception {
        Workload w = Workload.closed(
                ClosedStep.constantConcurrent(5, Duration.ofSeconds(1)),
                ClosedStep.rampConcurrent(0, 3, Duration.ofSeconds(2)),
                ClosedStep.rampConcurrent(3, 0, Duration.ofMillis(1500)));

        @SuppressWarnings("unchecked")
        List<ClosedInjectionStep> actual = (List<ClosedInjectionStep>) inject("closedInjection", w);

        List<ClosedInjectionStep> expected = List.of(
                constantConcurrentUsers(5).during(Duration.ofSeconds(1)),
                rampConcurrentUsers(0).to(3).during(Duration.ofSeconds(2)),
                rampConcurrentUsers(3).to(0).during(Duration.ofMillis(1500)));
        assertEquals(actual.size(), expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(scala(actual.get(i)), scala(expected.get(i)), "step " + i);
        }
    }

    /** Calls {@code TestFlyGatlingSimulation.<method>(scenarios[0].workload)} for the given workload. */
    private static Object inject(String method, Workload w) throws Exception {
        LoadScenario s = LoadScenario.named("Map").baseUrl("http://127.0.0.1:1").get("/a").workload(w);
        Class<?> configType = Class.forName("io.testfly.loadtest.internal.GatlingRunConfig");
        Constructor<?> ctor = configType.getDeclaredConstructor(LoadScenario.class, LoadTestConfig.class, String.class);
        ctor.setAccessible(true);
        Object cfg = ctor.newInstance(s, LoadTestConfig.resolveFor(s, null, null), "http://127.0.0.1:1");
        java.lang.reflect.Field scenarios = configType.getField("scenarios");
        scenarios.setAccessible(true);
        Object scenario = ((List<?>) scenarios.get(cfg)).get(0);
        java.lang.reflect.Field field = scenario.getClass().getField("workload");
        field.setAccessible(true);
        Object workload = field.get(scenario);

        Class<?> sim = Class.forName("io.testfly.loadtest.internal.TestFlyGatlingSimulation");
        Method m = sim.getDeclaredMethod(method, workload.getClass());
        m.setAccessible(true);
        return m.invoke(null, workload);
    }

    private static String scala(Object javaStep) {
        try {
            return String.valueOf(javaStep.getClass().getMethod("asScala").invoke(javaStep));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no asScala() on " + javaStep.getClass(), e);
        }
    }

    private static String scala(List<?> steps) {
        return steps.stream().map(WorkloadInjectionMappingTest::scala).toList().toString();
    }
}
