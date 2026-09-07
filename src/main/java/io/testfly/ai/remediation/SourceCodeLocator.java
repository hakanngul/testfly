package io.testfly.ai.remediation;

import io.testfly.api.TestFlyApi;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the source code file and snippet around a test failure location.
 *
 * <p>Inspects the stack trace of a failed test to locate the user's test or page
 * class, filters out framework and runtime internals, and extracts the code context.
 */
@TestFlyApi(since = "1.9.0")
public final class SourceCodeLocator {

    private static final Pattern STACK_LINE_PATTERN =
            Pattern.compile("^\\s*at\\s+([a-zA-Z0-9_$.]+)\\.([a-zA-Z0-9_$]+)\\(([^:]+):(\\d+)\\)");

    private static final String[] IGNORED_PREFIXES = {
            "io.testfly.wait.",
            "io.testfly.driver.",
            "io.testfly.internal.",
            "io.testfly.listeners.",
            "io.testfly.healing.",
            "io.testfly.steps.",
            "io.testfly.assertion.",
            "io.testfly.test.support.",
            "io.testfly.test.BaseTest",
            "io.testfly.test.BasePage",
            "io.testfly.locator.",
            "org.testng.",
            "org.junit.",
            "java.",
            "jdk.",
            "sun.",
            "org.openqa.selenium.",
            "org.apache.maven.",
            "net.bytebuddy.",
            "com.intellij.",
            "org.gradle."
    };

    private SourceCodeLocator() {}

    /**
     * Resolves the source snippet from a stack trace string.
     *
     * @param stackTrace full stack trace string
     * @return {@link SourceSnippet} around the failure point, or {@code null} if not found
     */
    public static SourceSnippet findFailureSnippet(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return null;
        }

        String[] lines = stackTrace.split("\n");
        for (String line : lines) {
            Matcher m = STACK_LINE_PATTERN.matcher(line.trim());
            if (m.find()) {
                String fullClass = m.group(1);
                String fileName = m.group(3);
                int lineNumber;
                try {
                    lineNumber = Integer.parseInt(m.group(4));
                } catch (NumberFormatException e) {
                    continue;
                }

                // Skip internal framework and third-party classes
                if (isIgnoredClass(fullClass)) {
                    continue;
                }

                SourceSnippet snippet = extractSnippet(fullClass, fileName, lineNumber);
                if (snippet != null) {
                    return snippet;
                }
            }
        }

        return null;
    }

    /**
     * Resolves the source snippet from an array of {@link StackTraceElement}.
     */
    public static SourceSnippet findFailureSnippet(StackTraceElement[] stackTrace) {
        if (stackTrace == null || stackTrace.length == 0) {
            return null;
        }

        for (StackTraceElement elem : stackTrace) {
            String fullClass = elem.getClassName();
            if (isIgnoredClass(fullClass)) {
                continue;
            }

            int line = elem.getLineNumber();
            String fileName = elem.getFileName();
            if (line > 0 && fileName != null) {
                SourceSnippet snippet = extractSnippet(fullClass, fileName, line);
                if (snippet != null) {
                    return snippet;
                }
            }
        }

        return null;
    }

    private static boolean isIgnoredClass(String className) {
        for (String prefix : IGNORED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static SourceSnippet extractSnippet(String fullClass, String fileName, int targetLine) {
        // Strip inner class names ($1, $SubClass)
        String baseClass = fullClass.contains("$") ? fullClass.substring(0, fullClass.indexOf('$')) : fullClass;
        String relPath = baseClass.replace('.', File.separatorChar) + ".java";

        File baseDir = new File(System.getProperty("user.dir", "."));
        File[] candidateRoots = new File[] {
                new File(baseDir, "src" + File.separator + "test" + File.separator + "java"),
                new File(baseDir, "src" + File.separator + "main" + File.separator + "java"),
                baseDir
        };

        File resolvedFile = null;
        for (File root : candidateRoots) {
            File f = new File(root, relPath);
            if (f.exists() && f.isFile()) {
                resolvedFile = f;
                break;
            }
        }

        if (resolvedFile == null) {
            return null;
        }

        try {
            List<String> allLines = Files.readAllLines(resolvedFile.toPath(), StandardCharsets.UTF_8);
            int total = allLines.size();
            if (total == 0 || targetLine > total) {
                return null;
            }

            int start = Math.max(1, targetLine - 10);
            int end = Math.min(total, targetLine + 10);

            StringBuilder sb = new StringBuilder();
            String targetLineContent = "";

            for (int i = start; i <= end; i++) {
                String line = allLines.get(i - 1);
                if (i == targetLine) {
                    targetLineContent = line;
                    sb.append(String.format("%5d: -> %s%n", i, line));
                } else {
                    sb.append(String.format("%5d:    %s%n", i, line));
                }
            }

            String relToProject = baseDir.toPath().relativize(resolvedFile.toPath()).toString();
            return new SourceSnippet(resolvedFile, relToProject, targetLine, start, end, sb.toString(), targetLineContent);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Source code snippet context for a failure location.
     */
    public record SourceSnippet(
            File file,
            String relativePath,
            int lineNumber,
            int startLine,
            int endLine,
            String contextCode,
            String targetLine
    ) {}
}
