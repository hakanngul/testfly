package io.testfly.unit.browser;

import io.testfly.browser.DownloadManager;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link DownloadManager}.
 * Uses real temporary directories for filesystem operations — no browser
 * needed.
 */
@Test(singleThreaded = true)
public class DownloadManagerTest {

    private MockedStatic<TestFlyContext> contextMock;
    private Path tempDir;

    @BeforeMethod
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("testfly-dl-test-");

        TestFlyConfig.Browser browserConfig = new TestFlyConfig.Browser();
        browserConfig.setDownloadDir(tempDir.toString());
        TestFlyConfig config = new TestFlyConfig();
        config.setBrowser(browserConfig);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void tearDown() {
        if (contextMock != null)
            contextMock.close();
        // Clean up temp directory
        if (tempDir != null) {
            try {
                Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------
    // waitForFile
    // ---------------------------------------------------------------

    @Test
    public void waitForFile_fileAlreadyExists_returnsFile() throws IOException {
        File target = new File(tempDir.toFile(), "report.csv");
        Files.writeString(target.toPath(), "id,name\n1,Alice\n");

        File result = DownloadManager.waitForFile("report.csv", 2);

        assertNotNull(result);
        assertEquals(result.getName(), "report.csv");
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
    }

    @Test
    public void waitForFile_fileAppearsLater_returnsFile() throws Exception {
        // Create the file after a short delay on a background thread
        Thread creator = new Thread(() -> {
            try {
                Thread.sleep(300);
                File target = new File(tempDir.toFile(), "delayed.csv");
                Files.writeString(target.toPath(), "data");
            } catch (Exception e) {
                // ignore
            }
        });
        creator.start();

        File result = DownloadManager.waitForFile("delayed.csv", 5);

        assertNotNull(result);
        assertEquals(result.getName(), "delayed.csv");
        creator.join();
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void waitForFile_fileNeverAppears_throwsOnTimeout() {
        // Empty directory — file will never appear
        DownloadManager.waitForFile("nonexistent.csv", 1);
    }

    @Test
    public void waitForFile_timeoutMessage_includesFilename() {
        try {
            DownloadManager.waitForFile("expected-report.xlsx", 1);
            assertTrue(false, "Expected RuntimeException");
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("expected-report.xlsx"),
                    "Error message should mention the filename, was: " + msg);
            assertTrue(msg.contains("DownloadManager"),
                    "Error message should mention DownloadManager, was: " + msg);
        }
    }

    @Test
    public void waitForFile_emptyFileIgnored_waitsForContent() throws Exception {
        // Create a zero-byte file first (simulates download in progress)
        File emptyFile = new File(tempDir.toFile(), "partial.csv");
        emptyFile.createNewFile();
        // emptyFile.length() == 0, so waitForFile should ignore it

        // Write content after a short delay
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(400);
                try {
                    Files.writeString(emptyFile.toPath(), "actual content");
                } catch (IOException e) {
                    // ignore
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        writer.start();

        File result = DownloadManager.waitForFile("partial.csv", 5);
        assertNotNull(result);
        assertTrue(result.length() > 0);
        writer.join();
    }

    // ---------------------------------------------------------------
    // waitForAnyFile
    // ---------------------------------------------------------------

    @Test
    public void waitForAnyFile_fileExists_returnsIt() throws IOException {
        File target = new File(tempDir.toFile(), "any-file.txt");
        Files.writeString(target.toPath(), "some content");

        File result = DownloadManager.waitForAnyFile(2);

        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void waitForAnyFile_emptyDirectory_throwsOnTimeout() {
        DownloadManager.waitForAnyFile(1);
    }

    // ---------------------------------------------------------------
    // clearDownloads
    // ---------------------------------------------------------------

    @Test
    public void clearDownloads_deletesAllFiles() throws IOException {
        Files.writeString(tempDir.resolve("file1.csv"), "data");
        Files.writeString(tempDir.resolve("file2.pdf"), "data");
        Files.writeString(tempDir.resolve("file3.xlsx"), "data");

        assertEquals(tempDir.toFile().listFiles().length, 3);

        DownloadManager.clearDownloads();

        File[] remaining = tempDir.toFile().listFiles(f -> f.isFile());
        assertNotNull(remaining);
        assertEquals(remaining.length, 0, "All files should be deleted");
    }

    @Test
    public void clearDownloads_emptyDirectory_noError() {
        // Should not throw on empty directory
        DownloadManager.clearDownloads();
    }

    // ---------------------------------------------------------------
    // resolveDownloadDir
    // ---------------------------------------------------------------

    @Test
    public void resolveDownloadDir_returnsConfiguredDirectory() {
        File dir = DownloadManager.resolveDownloadDir();

        assertNotNull(dir);
        assertTrue(dir.exists(), "Download directory should be created if absent");
        assertTrue(dir.isDirectory());
    }

    @Test
    public void resolveDownloadDir_createsDirectoryIfMissing() throws IOException {
        Path uniqueDir = Files.createTempDirectory("testfly-dl-resolve-");
        // Delete it so resolveDownloadDir has to recreate it
        Files.delete(uniqueDir);
        assertFalse(uniqueDir.toFile().exists(), "Directory should not exist before test");

        try {
            TestFlyConfig.Browser browserConfig = new TestFlyConfig.Browser();
            browserConfig.setDownloadDir(uniqueDir.toString());
            TestFlyConfig config = new TestFlyConfig();
            config.setBrowser(browserConfig);

            // Re-stub the existing mock with the new config
            contextMock.when(TestFlyContext::getConfig).thenReturn(config);
            File dir = DownloadManager.resolveDownloadDir();
            assertTrue(dir.exists(), "resolveDownloadDir should create the directory");
        } finally {
            // Clean up
            uniqueDir.toFile().delete();
        }
    }
}
