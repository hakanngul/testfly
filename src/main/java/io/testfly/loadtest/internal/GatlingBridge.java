package io.testfly.loadtest.internal;

/**
 * Classpath probe for Gatling — determines whether the Gatling engine is available.
 *
 * <p>Follows the same pattern as {@code ReportPortalJUnit5Bridge}: a static
 * {@code Class.forName} probe with {@code initialize=false} so Gatling's static
 * initializers are never triggered by the probe alone.
 *
 * <p>When Gatling is absent, {@link #isAvailable()} returns {@code false} and
 * {@link GatlingEngine} is never instantiated — avoiding {@code NoClassDefFoundError}.
 */
public final class GatlingBridge {

    private static final String GATLING_SIMULATION_CLASS = "io.gatling.javaapi.core.Simulation";
    private static final String GATLING_APP_CLASS = "io.gatling.app.Gatling";
    private static final boolean AVAILABLE;

    static {
        boolean ok;
        try {
            ClassLoader cl = GatlingBridge.class.getClassLoader();
            Class.forName(GATLING_SIMULATION_CLASS, false, cl);
            Class.forName(GATLING_APP_CLASS, false, cl);
            ok = true;
        } catch (ClassNotFoundException e) {
            ok = false;
        }
        AVAILABLE = ok;
    }

    private GatlingBridge() {}

    /** Returns {@code true} if Gatling is on the classpath. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Checks whether {@code java.base/java.lang} is opened to unnamed module.
     * Gatling uses {@link java.lang.invoke.MethodHandles#privateLookupIn} on Java 17+,
     * which requires {@code --add-opens java.base/java.lang=ALL-UNNAMED}.
     */
    public static boolean isJavaLangOpened() {
        try {
            java.lang.invoke.MethodHandles.privateLookupIn(
                    String.class, java.lang.invoke.MethodHandles.lookup());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns a user-friendly error message with the Maven dependency snippet
     * when Gatling is not available.
     */
    public static String missingDependencyMessage() {
        return "[LoadTest] Gatling engine requires 'io.gatling.highcharts:gatling-charts-highcharts' " +
                "on the classpath. Add to your pom.xml:\n\n" +
                "  <dependency>\n" +
                "    <groupId>io.gatling.highcharts</groupId>\n" +
                "    <artifactId>gatling-charts-highcharts</artifactId>\n" +
                "    <version>3.13.5</version>\n" +
                "    <scope>test</scope>\n" +
                "  </dependency>\n\n" +
                "Or use engine: jdk for the zero-dependency fallback.";
    }
}
