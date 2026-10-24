package io.testfly.examples.mobile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test credentials for the mobile examples: the environment variable, else the same key from the
 * git-ignored {@code .vscode/mobile.env}. Never write credentials in tests, features or YAML.
 */
public final class MobileEnv {

    private static final String ENV_FILE = ".vscode/mobile.env";

    private MobileEnv() {
    }

    /** The value of {@code name}; fails the test with a clear message when it is not set. */
    public static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = fromEnvFile(name);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " (or " + ENV_FILE + ") is required");
        }
        return value;
    }

    private static String fromEnvFile(String name) {
        Path file = Path.of(ENV_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(name + "=")) {
                    return trimmed.substring(name.length() + 1);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
