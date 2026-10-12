package io.testfly.steps;

public class StepRecord {

    private final String name;
    private final long   offsetMs;
    private final String status;
    private final String screenshotBase64;
    private final String ariaSnapshot;
    private final String domSnapshot;
    private volatile String afterAriaSnapshot;

    public StepRecord(String name, long offsetMs, String status, String screenshotBase64) {
        this(name, offsetMs, status, screenshotBase64, null, null);
    }

    /**
     * @param ariaSnapshot accessibility tree (YAML) taken when the step started, or {@code null}
     * @param domSnapshot  pruned page HTML taken when the step started, or {@code null}
     */
    public StepRecord(String name, long offsetMs, String status, String screenshotBase64,
                      String ariaSnapshot, String domSnapshot) {
        this.name              = name;
        this.offsetMs          = offsetMs;
        this.status            = status;
        this.screenshotBase64  = screenshotBase64;
        this.ariaSnapshot      = ariaSnapshot;
        this.domSnapshot       = domSnapshot;
    }

    public String getName()              { return name; }
    public long   getOffsetMs()          { return offsetMs; }
    public String getStatus()            { return status; }
    public String getScreenshotBase64()  { return screenshotBase64; }
    public String getAriaSnapshot()      { return ariaSnapshot; }
    public String getDomSnapshot()       { return domSnapshot; }

    /** Accessibility tree taken after the step, recorded only for the last step of a failed test. */
    public String getAfterAriaSnapshot() { return afterAriaSnapshot; }
    public void   setAfterAriaSnapshot(String snapshot) { this.afterAriaSnapshot = snapshot; }
}
