package io.testfly.mobile;

/** A crash report on the device: its id, the crashed process and when it was written (as the device reports it). */
public record MobileCrashReport(String id, String processName, String timestamp) {
}
