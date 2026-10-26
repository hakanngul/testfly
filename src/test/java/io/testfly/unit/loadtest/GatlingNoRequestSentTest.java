package io.testfly.unit.loadtest;

import io.testfly.loadtest.internal.GatlingResultsParser;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.testng.Assert.*;

/**
 * R-18 second half (plan item 2): Gatling 3.16 exits with code 1 ("no requests sent, reports won't be generated")
 * when every request of a run fails to build, after {@code TestFlyGatlingSimulation.after()} has already written
 * a complete sidecar. Before treating exit code 1 as a crash, {@code GatlingEngine} must ask the sidecar whether
 * the run simply sent no request; if so, the run is parsed normally (every request KO with status -1).
 *
 * <p>The decision helper the implementation must provide (package-private, reached by reflection like
 * {@link SlaApi#checkExitCode}):
 * <pre>{@code static boolean noRequestCouldBeSent(java.nio.file.Path sidecar)}</pre>
 * on {@code io.testfly.loadtest.internal.GatlingEngine}. It returns {@code true} only if
 * <ul>
 *   <li>the sidecar is readable, a JSON object with a supported {@code schemaVersion}, and complete:
 *       {@code endMs} present (the file is written atomically by {@code after()});</li>
 *   <li>the total {@code attempts} over the step entries is greater than 0;</li>
 *   <li>no entry (step or redirect hop) has a status code, and none has a latency sample.</li>
 * </ul>
 * Anything else (missing, unreadable or malformed file, no {@code endMs}, no attempts, any response) is
 * {@code false}, never an exception: exit code 1 then keeps the crash path.
 */
@Test(singleThreaded = true)
public class GatlingNoRequestSentTest {

    private static final String GATLING_ENGINE = "io.testfly.loadtest.internal.GatlingEngine";
    private static final String HELPER = "noRequestCouldBeSent";

    /** Every request of the only step failed to build: attempts and KO, no status, no latency. */
    private static final String ALL_UNBUILDABLE = """
            {"schemaVersion":2,"startMs":1000000,"endMs":1003000,"maxDurationMs":60000,
             "scenarios":{"S":["get"]},
             "requests":{"get":{"attempts":6,"ok":0,"ko":6}}}
            """;

    /** Same with explicit empty status and latency maps, and two steps. */
    private static final String ALL_UNBUILDABLE_EMPTY_MAPS = """
            {"schemaVersion":2,"startMs":1000000,"endMs":1003000,"maxDurationMs":60000,
             "scenarios":{"S":["a","b"]},
             "requests":{
               "a":{"attempts":3,"ok":0,"ko":3,"status":{},"latencyMs":{}},
               "b":{"attempts":2,"ok":0,"ko":2,"status":{},"latencyMs":{}}}}
            """;

    @Test
    public void helperExists_packagePrivateStatic_returnsBoolean() throws Exception {
        Method m = helper();
        assertTrue(Modifier.isStatic(m.getModifiers()), "noRequestCouldBeSent must be static");
        assertFalse(Modifier.isPublic(m.getModifiers()), "noRequestCouldBeSent is internal: not public");
        assertEquals(m.getReturnType(), boolean.class);
    }

    @Test
    public void allRequestsUnbuildable_isNoRequestSent() throws Exception {
        assertTrue(decide(ALL_UNBUILDABLE), "attempts > 0, no status, no latency, endMs present");
        assertTrue(decide(ALL_UNBUILDABLE_EMPTY_MAPS), "empty status/latency maps count as no response");
    }

    @Test
    public void schemaVersion1_isAlsoUnderstood() throws Exception {
        assertTrue(decide("""
                {"schemaVersion":1,"startMs":1000000,"endMs":1001000,
                 "requests":{"get":{"attempts":2,"ok":0,"ko":2}}}
                """));
    }

    @Test
    public void missingEndMs_isNotComplete_soFalse() throws Exception {
        assertFalse(decide("""
                {"schemaVersion":2,"startMs":1000000,"maxDurationMs":60000,
                 "scenarios":{"S":["get"]},
                 "requests":{"get":{"attempts":6,"ok":0,"ko":6}}}
                """), "a sidecar without endMs was not completed by after()");
    }

    @Test
    public void zeroAttempts_isFalse() throws Exception {
        assertFalse(decide("""
                {"schemaVersion":2,"startMs":1000000,"endMs":1003000,
                 "scenarios":{"S":[]},"requests":{}}
                """), "no attempts: Gatling never reached a step; that is a crash, not an unbuildable run");
        assertFalse(decide("""
                {"schemaVersion":2,"startMs":1000000,"endMs":1003000,
                 "scenarios":{"S":["get"]},"requests":{"get":{"attempts":0,"ok":0,"ko":0}}}
                """));
    }

    @Test
    public void anyStatusCode_isFalse() throws Exception {
        assertFalse(decide("""
                {"schemaVersion":2,"startMs":1000000,"endMs":1003000,
                 "scenarios":{"S":["a","b"]},
                 "requests":{
                   "a":{"attempts":3,"ok":0,"ko":3},
                   "b":{"attempts":2,"ok":2,"ko":0,"status":{"200":2}}}}
                """), "a step got responses: requests were sent");
    }

    @Test
    public void statusOnARedirectHopOnly_isFalse() throws Exception {
        assertFalse(decide("""
                {"schemaVersion":2,"startMs":1000000,"endMs":1003000,
                 "scenarios":{"S":["a"]},
                 "requests":{
                   "a":{"attempts":3,"ok":0,"ko":3},
                   "a Redirect 1":{"status":{"200":1},"latencyMs":{"5":1}}}}
                """), "a redirect hop got a response: requests were sent");
    }

    @Test
    public void latencySamplesWithoutStatus_isFalse() throws Exception {
        assertFalse(decide("""
                {"schemaVersion":2,"startMs":1000000,"endMs":1003000,
                 "scenarios":{"S":["a"]},
                 "requests":{"a":{"attempts":3,"ok":0,"ko":3,"latencyMs":{"12":3}}}}
                """), "latency samples mean requests were timed, so they were sent");
    }

    @Test
    public void missingMalformedOrUnsupportedSidecar_isFalse_notAnException() throws Exception {
        Path dir = Files.createTempDirectory("no-request-sent-");
        try {
            assertFalse(invoke(dir.resolve("absent.json")), "missing sidecar");
            assertFalse(invoke(write(dir, "truncated.json", "{\"schemaVersion\":2,\"startMs\":1,")), "malformed JSON");
            assertFalse(invoke(write(dir, "array.json", "[]")), "not a JSON object");
            assertFalse(invoke(write(dir, "future.json", """
                    {"schemaVersion":99,"startMs":1000000,"endMs":1003000,
                     "requests":{"get":{"attempts":6,"ok":0,"ko":6}}}
                    """)), "unsupported schemaVersion");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void fixturesAreParseableSidecars() throws Exception {
        // the positive fixtures are real sidecars: the parser reads them as every request KO with status -1
        Path dir = Files.createTempDirectory("no-request-sent-parse-");
        try {
            write(dir, GatlingResultsParser.METRICS_SIDECAR_FILE, ALL_UNBUILDABLE);
            io.testfly.loadtest.LoadTestMetrics m = GatlingResultsParser.parse(dir.toString(), "S", 1);
            assertEquals(m.totalRequests(), 6);
            assertEquals(m.failedRequests(), 6);
            assertEquals(m.statusCodeCount(-1), 6L, "statusCodes=" + m.statusCodes());
            assertFalse(m.partial(), "attempts == ok + ko");
        } finally {
            deleteRecursively(dir);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static Method helper() {
        try {
            Method m = Class.forName(GATLING_ENGINE).getDeclaredMethod(HELPER, Path.class);
            m.setAccessible(true);
            return m;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // a missing API is reported as such, not as an assertion failure
            throw new IllegalStateException("missing API: static boolean " + GATLING_ENGINE + "." + HELPER
                    + "(java.nio.file.Path)", e);
        }
    }

    private static boolean decide(String sidecarJson) throws Exception {
        Path dir = Files.createTempDirectory("no-request-sent-");
        try {
            return invoke(write(dir, GatlingResultsParser.METRICS_SIDECAR_FILE, sidecarJson));
        } finally {
            deleteRecursively(dir);
        }
    }

    private static boolean invoke(Path sidecar) throws Exception {
        try {
            return (boolean) helper().invoke(null, sidecar);
        } catch (InvocationTargetException e) {
            throw new AssertionError(HELPER + " must answer false instead of throwing for " + sidecar,
                    e.getCause());
        }
    }

    private static Path write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
