package io.testfly.unit;

import io.testfly.internal.FilePaths;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/** Unit tests for {@link FilePaths#resolve(String)}. */
public class FilePathsTest {

    @Test
    public void resolve_existingAbsolutePath_returnsIt() throws Exception {
        Path tmp = Files.createTempFile("testfly-filepaths", ".txt");
        try {
            assertEquals(FilePaths.resolve(tmp.toString()), new File(tmp.toString()).getAbsolutePath());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void resolve_classpathResource_returnsAbsolutePath() {
        String resolved = FilePaths.resolve("logback.xml");
        assertTrue(new File(resolved).isAbsolute());
        assertTrue(resolved.endsWith("logback.xml"));
    }

    @Test
    public void resolve_projectRelativePath_returnsAbsolutePath() {
        String resolved = FilePaths.resolve("pom.xml");
        assertEquals(resolved, new File(System.getProperty("user.dir"), "pom.xml").getAbsolutePath());
    }

    @Test
    public void resolve_missingFile_throws() {
        assertThrows(IllegalArgumentException.class, () -> FilePaths.resolve("does/not/exist-" + System.nanoTime()));
    }
}
