package io.testfly.internal;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;

/**
 * Internal helper that resolves a user-supplied file path (e.g. for uploads) to
 * an absolute path. Not part of the public API.
 */
public final class FilePaths {

    private FilePaths() {
    }

    /**
     * Resolves {@code filePath} by checking, in order: an existing absolute path,
     * a classpath resource (e.g. {@code src/test/resources}), and a path relative
     * to the project root ({@code user.dir}).
     *
     * @throws IllegalArgumentException if the file cannot be found
     */
    public static String resolve(String filePath) {
        // 1. Absolute path
        File absolute = new File(filePath);
        if (absolute.isAbsolute() && absolute.exists()) {
            return absolute.getAbsolutePath();
        }

        // 2. Classpath resource (src/test/resources)
        URL resource = findResource(filePath);
        if (resource != null) {
            try {
                return Paths.get(resource.toURI()).toAbsolutePath().toString();
            } catch (Exception ignored) {
            }
        }

        // 3. Project-root relative
        File relative = Paths.get(System.getProperty("user.dir"), filePath).toFile();
        if (relative.exists()) {
            return relative.getAbsolutePath();
        }

        throw new IllegalArgumentException(
                "File not found for upload: '" + filePath + "'. " +
                        "Checked: absolute path, classpath resources, and project-root relative path.");
    }

    private static URL findResource(String filePath) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        URL resource = context != null ? context.getResource(filePath) : null;
        return resource != null ? resource : FilePaths.class.getClassLoader().getResource(filePath);
    }
}
