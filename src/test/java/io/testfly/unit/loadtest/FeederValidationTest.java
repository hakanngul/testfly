package io.testfly.unit.loadtest;

import com.sun.net.httpserver.HttpServer;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadSimulation;
import io.testfly.loadtest.LoadTestFeeder;
import io.testfly.loadtest.Workload;
import io.testfly.loadtest.internal.GatlingBridge;
import io.testfly.loadtest.internal.LoadScenarioValidator;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.testng.Assert.*;

/**
 * R-18 (plan item 1): a csv or json file feeder that resolves to no rows (file not found, or a CSV with only a
 * header, or an empty JSON array) fails fast in {@link LoadScenarioValidator#validate} with an
 * {@link IllegalArgumentException} that names the path and says "not found or has no rows". The rejection
 * happens before any request on both engines and inside the simulation validation.
 *
 * <p>{@code LoadTestFeeder.csv(..)}/{@code json(..)} themselves still do not throw (public contract, covered by
 * {@code LoadTestFeederTest} and {@code LoadTestFeederFileTest}); only validation rejects the empty feeder.
 * Every request goes to a local 127.0.0.1 server whose counter must stay at 0.
 */
@Test(singleThreaded = true)
public class FeederValidationTest {

    /** The words the message must contain (with the path). */
    private static final String NO_ROWS = "not found or has no rows";
    private static final String MISSING_CSV = "does/not/exist.csv";
    private static final String MISSING_JSON = "does/not/exist.json";

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private String baseUrl;
    private Path tempDir;
    private String headerOnlyCsv;
    private String emptyJsonArray;

    @BeforeClass
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        tempDir = Files.createTempDirectory("feeder-validation-");
        Path csv = tempDir.resolve("header-only.csv");
        Files.writeString(csv, "username,password\n", StandardCharsets.UTF_8);
        headerOnlyCsv = csv.toAbsolutePath().toString();
        Path json = tempDir.resolve("empty.json");
        Files.writeString(json, "[]", StandardCharsets.UTF_8);
        emptyJsonArray = json.toAbsolutePath().toString();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        if (tempDir != null) {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private static LoadScenario fed(String name, LoadTestFeeder feeder) {
        return LoadScenario.named(name).feed(feeder).step("s").get("/x?u=#{username}").and();
    }

    private static void assertRejected(LoadScenario s, String engine, String path) {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validate(s, engine));
        assertMessage(e, s.name(), path);
    }

    private static void assertMessage(IllegalArgumentException e, String scenario, String path) {
        String msg = e.getMessage();
        assertNotNull(msg, "the rejection needs a message");
        assertTrue(msg.contains(path), "message must name the feeder path '" + path + "': " + msg);
        assertTrue(msg.contains(NO_ROWS), "message must say '" + NO_ROWS + "': " + msg);
        assertTrue(msg.startsWith("[LoadTest] Scenario '" + scenario + "'"),
                "message must follow the validator's '[LoadTest] Scenario '<name>'' prefix: " + msg);
    }

    // ── the factories keep their contract ────────────────────────────────

    @Test
    public void factories_stillDoNotThrow_forMissingOrEmptyFiles() {
        // validation, not construction, rejects the feeder
        LoadTestFeeder csv = LoadTestFeeder.csv(MISSING_CSV);
        LoadTestFeeder json = LoadTestFeeder.json(MISSING_JSON);
        LoadTestFeeder headerOnly = LoadTestFeeder.csv(headerOnlyCsv);
        assertFalse(csv.hasNext());
        assertFalse(json.hasNext());
        assertFalse(headerOnly.hasNext(), "a CSV with only a header has no rows");
    }

    // ── validator, both engines ──────────────────────────────────────────

    @Test
    public void missingCsv_isRejectedOnBothEngines() {
        for (String engine : new String[] {"jdk", "gatling"}) {
            assertRejected(fed("MissingCsv", LoadTestFeeder.csv(MISSING_CSV)), engine, MISSING_CSV);
        }
    }

    @Test
    public void missingJson_isRejectedOnBothEngines() {
        for (String engine : new String[] {"jdk", "gatling"}) {
            assertRejected(fed("MissingJson", LoadTestFeeder.json(MISSING_JSON)), engine, MISSING_JSON);
        }
    }

    @Test
    public void csvWithHeaderButNoDataRows_isRejectedOnBothEngines() {
        for (String engine : new String[] {"jdk", "gatling"}) {
            assertRejected(fed("HeaderOnly", LoadTestFeeder.csv(headerOnlyCsv)), engine, headerOnlyCsv);
        }
    }

    @Test
    public void jsonWithEmptyArray_isRejectedOnBothEngines() {
        for (String engine : new String[] {"jdk", "gatling"}) {
            assertRejected(fed("EmptyJson", LoadTestFeeder.json(emptyJsonArray)), engine, emptyJsonArray);
        }
    }

    @Test
    public void feedCsvAndFeedJsonShortcuts_areRejectedToo() {
        LoadScenario csv = LoadScenario.named("ShortcutCsv").feedCsv(MISSING_CSV).step("s").get("/x").and();
        LoadScenario json = LoadScenario.named("ShortcutJson").feedJson(MISSING_JSON).step("s").get("/x").and();
        assertRejected(csv, "jdk", MISSING_CSV);
        assertRejected(json, "gatling", MISSING_JSON);
    }

    @Test
    public void filesWithRows_andGeneratedFeeders_stillPass() {
        // guards against an over-eager rule: real rows and non-file feeders are valid
        for (String engine : new String[] {"jdk", "gatling"}) {
            LoadScenarioValidator.validate(fed("Rows", LoadTestFeeder.csv("loadtest/users.csv")), engine);
            LoadScenarioValidator.validate(fed("JsonRows", LoadTestFeeder.json("loadtest/products.json")), engine);
            LoadScenarioValidator.validate(fed("Seq", LoadTestFeeder.sequence("username", 1, 1)), engine);
            LoadScenarioValidator.validate(fed("Const", LoadTestFeeder.constant("username", "u")), engine);
            LoadScenarioValidator.validate(LoadScenario.named("NoFeeder").step("s").get("/x").and(), engine);
        }
    }

    /** A custom feeder that counts every call: validation must not consume, sample or reset it. */
    private static final class CountingFeeder extends LoadTestFeeder {
        final AtomicInteger nextCalls = new AtomicInteger();
        final AtomicInteger resetCalls = new AtomicInteger();
        private final boolean hasRows;

        CountingFeeder(boolean hasRows) {
            this.hasRows = hasRows;
        }

        @Override
        public java.util.Map<String, Object> next() {
            nextCalls.incrementAndGet();
            return java.util.Map.of("username", "token-" + nextCalls.get());
        }

        @Override
        public void reset() {
            resetCalls.incrementAndGet();
        }

        @Override
        public boolean hasNext() {
            return hasRows;
        }
    }

    @Test
    public void validation_doesNotConsumeOrResetACustomFeeder() {
        for (boolean hasRows : new boolean[] {true, false}) {
            for (String engine : new String[] {"jdk", "gatling"}) {
                CountingFeeder feeder = new CountingFeeder(hasRows);
                LoadScenario s = fed("Custom", feeder);
                LoadScenarioValidator.validate(s, engine);
                LoadScenarioValidator.validate(s, engine); // the JDK path validates twice
                assertEquals(feeder.nextCalls.get(), 0, "next() calls during validation (" + engine + ")");
                assertEquals(feeder.resetCalls.get(), 0, "reset() calls during validation (" + engine + ")");
            }
        }
        CountingFeeder simFeeder = new CountingFeeder(true);
        LoadSimulation sim = LoadSimulation.named("CustomSim")
                .scenario(fed("CustomSim", simFeeder).workload(Workload.open(io.testfly.loadtest.OpenStep.atOnce(1))));
        LoadScenarioValidator.validateSimulation(sim, "gatling", 1000);
        assertEquals(simFeeder.nextCalls.get(), 0, "next() calls during simulation validation");
        assertEquals(simFeeder.resetCalls.get(), 0, "reset() calls during simulation validation");
    }

    // ── no request is sent ───────────────────────────────────────────────

    @Test
    public void jdkRun_withMissingCsv_failsBeforeAnyRequest() {
        int before = hits.get();
        LoadScenario s = LoadScenario.named("RunMissingCsv_" + uid()).baseUrl(baseUrl).engine("jdk")
                .users(1).rampUp(Duration.ZERO).hold(Duration.ofSeconds(1))
                .feed(LoadTestFeeder.csv(MISSING_CSV))
                // no variable in the path: today the JDK engine sends these requests unchanged
                .step("s").get("/fed").and();

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, s::run);
        assertTrue(e.getMessage() != null && e.getMessage().contains(MISSING_CSV)
                && e.getMessage().contains(NO_ROWS), "run() must fail with the feeder message: " + e.getMessage());
        assertEquals(hits.get(), before, "no request may be sent when the feeder has no rows");
    }

    @Test
    public void jdkRun_withEmptyJson_failsBeforeAnyRequest() {
        int before = hits.get();
        LoadScenario s = LoadScenario.named("RunEmptyJson_" + uid()).baseUrl(baseUrl).engine("jdk")
                .users(1).rampUp(Duration.ZERO).hold(Duration.ofSeconds(1))
                .feed(LoadTestFeeder.json(emptyJsonArray))
                .step("s").get("/fed").and();

        expectThrows(IllegalArgumentException.class, s::run);
        assertEquals(hits.get(), before, "no request may be sent when the feeder has no rows");
    }

    // ── simulation validation (Gatling only) ─────────────────────────────

    @Test
    public void simulationValidation_rejectsAScenarioWithAnEmptyFeeder() {
        LoadSimulation sim = LoadSimulation.named("FeederSim")
                .scenario(LoadScenario.named("Fine").step("a").get("/a").and()
                        .workload(Workload.open(io.testfly.loadtest.OpenStep.atOnce(1))))
                .scenario(LoadScenario.named("Fed").feed(LoadTestFeeder.csv(MISSING_CSV)).step("b").get("/b").and()
                        .workload(Workload.open(io.testfly.loadtest.OpenStep.atOnce(1))));
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> LoadScenarioValidator.validateSimulation(sim, "gatling", 1000));
        assertMessage(e, "Fed", MISSING_CSV);
    }

    @Test
    public void simulationRun_withAnEmptyFeeder_failsBeforeAnyRequest() {
        if (!GatlingBridge.isAvailable()) {
            throw new SkipException("Gatling is not on the classpath");
        }
        int before = hits.get();
        LoadSimulation sim = LoadSimulation.named("FeederSimRun_" + uid()).baseUrl(baseUrl).engine("gatling")
                .scenario(LoadScenario.named("Fed").feed(LoadTestFeeder.json(MISSING_JSON))
                        .step("b").get("/fed-sim").and()
                        .workload(Workload.open(io.testfly.loadtest.OpenStep.atOnce(1))));

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, sim::run);
        assertMessage(e, "Fed", MISSING_JSON);
        assertEquals(hits.get(), before, "no request may be sent when a scenario's feeder has no rows");
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
