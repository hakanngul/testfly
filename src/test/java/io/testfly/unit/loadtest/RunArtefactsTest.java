package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadBaseline;
import io.testfly.loadtest.LoadTestMetrics;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static org.testng.Assert.*;

/**
 * The per-run artefact {@value LoadBaseline#RESULT_FILE} (ADR 0004 decision 6): written by the internal
 * {@code RunArtefacts} helper of both engines. Writing it must never fail a run, and two run directories of the same
 * scenario never collide. Package-private helper, hence reflection.
 */
public class RunArtefactsTest {

    private static final String TYPE = "io.testfly.loadtest.internal.RunArtefacts";

    @Test
    public void writeResult_failure_isSwallowed_andWritesNothing() throws Exception {
        Path dir = Files.createTempDirectory("run-artefact-");
        try {
            Path notADir = Files.writeString(dir.resolve("plain-file"), "x"); // a file where the run dir should be
            Method write = Class.forName(TYPE).getDeclaredMethod("writeResult", Path.class, LoadTestMetrics.class);
            write.setAccessible(true);

            write.invoke(null, notADir, metrics()); // must not throw

            assertFalse(Files.exists(notADir.resolve(LoadBaseline.RESULT_FILE)));
            write.invoke(null, dir, metrics());
            assertEquals(LoadBaseline.load(dir.resolve(LoadBaseline.RESULT_FILE)).scenarioName(), "Artefact");
        } finally {
            delete(dir);
        }
    }

    @Test
    public void newRunDir_usesTheGatlingSlug_andNeverReusesAnExistingDirectory() throws Exception {
        Path base = Files.createTempDirectory("run-dirs-");
        try {
            Method newRunDir = Class.forName(TYPE).getDeclaredMethod("newRunDir", String.class, String.class);
            newRunDir.setAccessible(true);
            File a = (File) newRunDir.invoke(null, base.toString(), "My Scenario/1");
            File b = (File) newRunDir.invoke(null, base.toString(), "My Scenario/1");

            assertTrue(a.isDirectory() && b.isDirectory());
            assertNotEquals(a, b, "two runs never share a run directory");
            assertTrue(a.getName().matches("My_Scenario_1-\\d+"), a.getName());
            assertTrue(b.getName().matches("My_Scenario_1-\\d+"), b.getName());
        } finally {
            delete(base);
        }
    }

    private static LoadTestMetrics metrics() {
        return new LoadTestMetrics("Artefact", 10, 10, 0, 5, 5, 5, 5, 5, 5, 1, 9, 0.0, Map.of(200, 10L), 1000, 1,
                "jdk", Map.of(), 5);
    }

    private static void delete(Path dir) throws Exception {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
