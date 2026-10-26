package io.testfly.unit.loadtest;

import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * The crash message of a failed Gatling child JVM ({@code GatlingEngine.checkExitCode} → {@code logTail}): the root
 * error lines come first even when a long stack trace (Gatling 3.16) pushes them out of the last 20 log lines, and
 * they are redacted like the tail.
 */
public class GatlingEngineLogTailTest {

    private static final String HEADLINE = "Exception in thread \"main\" io.gatling.core.session.el.ElParserException: "
            + "Failed to parse /ok?x=#{unclosed with error ''}' expected but end of source found'";

    @Test
    public void rootErrorBeyondTheTail_isShownFirst() throws Exception {
        String msg = crashMessage(log(HEADLINE, null, 22));

        assertTrue(msg.contains("ElParserException") && msg.contains("unclosed"),
                "the root cause must survive a stack trace longer than the tail: " + msg);
        assertTrue(msg.contains("exit code 1"), msg);
        assertTrue(msg.indexOf("ElParserException") < msg.indexOf("at io.gatling.frame"),
                "the root cause comes before the tail: " + msg);
        assertEquals(count(msg, "ElParserException"), 1, "listed once: " + msg);
    }

    @Test
    public void lastCausedByLine_isIncluded() throws Exception {
        String msg = crashMessage(log(HEADLINE, "Caused by: java.lang.IllegalStateException: inner cause", 22));
        assertTrue(msg.contains("ElParserException") && msg.contains("inner cause"), msg);
    }

    @Test
    public void exceptionHeadlineWithoutThreadPrefix_isFound() throws Exception {
        String msg = crashMessage(log("io.gatling.core.session.el.ElParserException: Failed to parse #{unclosed",
                null, 25));
        assertTrue(msg.contains("ElParserException") && msg.contains("unclosed"), msg);
    }

    @Test
    public void rootErrorInsideTheTail_isNotRepeated() throws Exception {
        String msg = crashMessage(log(HEADLINE, null, 5));
        assertEquals(count(msg, "ElParserException"), 1, "already in the tail, not added again: " + msg);
    }

    @Test
    public void secretInTheRootErrorLine_isRedacted() throws Exception {
        String secretHeadline = "Exception in thread \"main\" java.lang.IllegalArgumentException: "
                + "bad url /login?token=s3cr3tValue42&x=#{unclosed";
        String msg = crashMessage(log(secretHeadline, null, 22));

        assertTrue(msg.contains("IllegalArgumentException") && msg.contains("unclosed"), msg);
        assertFalse(msg.contains("s3cr3tValue42"), "the secret must be redacted: " + msg);
        assertTrue(msg.contains("***"), "redacted with the mask: " + msg);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** About 150 lines: 100 info lines, the headline, an optional cause, then {@code frames} stack frames. */
    private static List<String> log(String headline, String causedBy, int frames) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            lines.add("12:00:0" + (i % 10) + " [INFO ] gatling startup line " + i);
        }
        lines.add(headline);
        for (int i = 0; i < frames; i++) {
            lines.add("\tat io.gatling.frame" + i + "(Frame.scala:" + i + ")");
            if (causedBy != null && i == frames / 2) {
                lines.add(causedBy);
            }
        }
        return lines;
    }

    private static String crashMessage(List<String> lines) throws Exception {
        Path dir = Files.createTempDirectory("gatling-log-tail-");
        try {
            File log = dir.resolve("gatling-subprocess.log").toFile();
            Files.write(log.toPath(), lines);
            IllegalStateException e = expectThrows(IllegalStateException.class,
                    () -> SlaApi.checkExitCode(1, "LogTail", log, false));
            return e.getMessage();
        } finally {
            for (File f : dir.toFile().listFiles()) {
                f.delete();
            }
            dir.toFile().delete();
        }
    }

    private static int count(String s, String part) {
        int n = 0;
        for (int i = s.indexOf(part); i >= 0; i = s.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }
}
