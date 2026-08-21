package io.testfly.reporting.reportportal;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Date;

/**
 * Sends log messages and file attachments directly to ReportPortal
 * via the {@code ReportPortal.emitLog()} API, bypassing the SLF4J/logback
 * layer.
 *
 * <p>This is necessary because the SLF4J → logback → RP appender pipeline
 * requires consumers to configure {@code logback.xml} with the RP appender.
 * Many consumer projects skip this step, causing logs to silently disappear.
 * Using {@code emitLog()} sends data straight to the RP client API regardless
 * of the consumer's logging backend configuration.
 *
 * <p>Requires {@code com.epam.reportportal:client-java} on the runtime
 * classpath (transitively provided by {@code agent-java-testng}).
 * When RP is not available, all methods are silent no-ops.
 */
public final class ReportPortalLogger {

    private static final String RP_CLASS = "com.epam.reportportal.service.ReportPortal";

    private static boolean available;
    private static Method emitLogText;
    private static Method emitLogFile;
    private static String initError;

    static {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = ReportPortalLogger.class.getClassLoader();
            Class<?> clazz = Class.forName(RP_CLASS, false, cl);
            // Use Date overload — proven to work reliably with RP agent
            emitLogText = clazz.getMethod("emitLog",
                    String.class, String.class, Date.class);
            emitLogFile = clazz.getMethod("emitLog",
                    String.class, String.class, Date.class, File.class);
            available = true;
            System.out.println("[TestFly] ReportPortalLogger: RP client API detected ✓");
        } catch (ClassNotFoundException e) {
            available = false;
            initError = "client-java not on classpath";
        } catch (NoSuchMethodException e) {
            available = false;
            initError = "emitLog method not found: " + e.getMessage();
        }
    }

    private ReportPortalLogger() {}

    /**
     * Emits a text log message to the current test item in ReportPortal.
     *
     * @param message the log message
     * @param level   the log level ({@code "INFO"}, {@code "ERROR"}, {@code "WARN"}, etc.)
     * @return {@code true} if the log was successfully emitted
     */
    public static boolean log(String message, String level) {
        if (!available || message == null) return false;
        try {
            Object result = emitLogText.invoke(null, message, level, new Date());
            boolean ok = Boolean.TRUE.equals(result);
            if (!ok) {
                System.err.println("[TestFly] ReportPortalLogger: emitLog returned false "
                        + "(no active RP test item?)");
            }
            return ok;
        } catch (Exception e) {
            System.err.println("[TestFly] ReportPortalLogger: emitLog failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Emits a log message with a file attachment to the current test item
     * in ReportPortal. The file is sent as a binary attachment (e.g. screenshot).
     *
     * @param message the log message
     * @param level   the log level ({@code "INFO"}, {@code "ERROR"}, etc.)
     * @param file    the file to attach (e.g. a PNG screenshot)
     * @return {@code true} if the log with attachment was successfully emitted
     */
    public static boolean logWithAttachment(String message, String level, File file) {
        if (!available || message == null || file == null || !file.exists()) return false;
        try {
            Object result = emitLogFile.invoke(null, message, level, new Date(), file);
            boolean ok = Boolean.TRUE.equals(result);
            if (!ok) {
                System.err.println("[TestFly] ReportPortalLogger: emitLog(file) returned false "
                        + "(no active RP test item? file=" + file.getName() + ")");
            }
            return ok;
        } catch (Exception e) {
            System.err.println("[TestFly] ReportPortalLogger: emitLog(file) failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code true} when the ReportPortal client library is on the
     * classpath and the {@code emitLog} methods were successfully resolved.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Returns the initialization error message, or {@code null} if
     * the RP API was resolved successfully.
     */
    public static String getInitError() {
        return initError;
    }
}
