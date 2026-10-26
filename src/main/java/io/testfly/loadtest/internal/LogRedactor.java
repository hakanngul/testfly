package io.testfly.loadtest.internal;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks credentials in engine log lines before they are put into exception messages.
 *
 * <p>Masked with {@code ***} (case-insensitive): the rest of the line after {@code Authorization},
 * {@code Cookie}/{@code Set-Cookie} and any header whose name contains auth/key/token/secret/signature/
 * cookie; the value after {@code Bearer}; values of keys containing {@code token}, {@code password},
 * {@code secret}, {@code api-key}/{@code api_key}/{@code apikey} (quoted values up to the closing
 * quote); and query parameters named key/sig/signature/token/apikey.
 */
public final class LogRedactor {

    public static final String MASK = "***";

    /** Header-style secrets: everything after the separator up to the end of the line. */
    private static final Pattern HEADER_LINE = Pattern.compile(
            "(?i)\\b((?:set-)?cookie|authorization)(\\\\?[\"']?\\s*[:=]\\s*)\\S.*");

    /**
     * Header lines whose NAME contains auth/key/token/secret/signature/cookie
     * ({@code X-Auth-Token: ..}, {@code x-api-key: ..}, {@code X-Amz-Signature: ..}): rest of the line.
     */
    private static final Pattern SENSITIVE_HEADER_NAME = Pattern.compile(
            "(?i)(?<![\\w-])([\\w-]*(?:auth|key|token|secret|signature|cookie)[\\w-]*)(\\s*:\\s+)\\S.*");

    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer)(\\s+)[^\\s\"',;&}\\]]+");

    private static final String SECRET_KEY = "([\\w-]*(?:token|password|passwd|secret|api[-_]?key)[\\w-]*)";

    /** Quoted values are masked up to the closing quote: {@code "password" : "my secret pass"}. */
    private static final Pattern KEY_QUOTED_VALUE = Pattern.compile(
            "(?i)" + SECRET_KEY + "(\\\\?[\"']?\\s*[:=]\\s*\\\\?)([\"'])(?:(?!\\3).)*\\3?");

    /** Unquoted key/value secrets: {@code token=..}, {@code api_key: ..}, ... */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)" + SECRET_KEY + "(\\\\?[\"']?\\s*[:=]\\s*)(?![\"'])[^\\s\"'\\\\,;&}\\]]+");

    /** Query parameters named key/sig/signature/token/apikey (and api_key/access_token). */
    private static final Pattern QUERY_PARAM = Pattern.compile(
            "(?i)([?&](?:key|sig|signature|token|apikey|api_key|access_token)=)[^&\\s#\"']+");

    private LogRedactor() {
    }

    /** Returns the line with credential values masked; {@code null} stays {@code null}. */
    public static String redact(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String out = HEADER_LINE.matcher(line).replaceAll("$1$2" + MASK);
        out = SENSITIVE_HEADER_NAME.matcher(out).replaceAll("$1$2" + MASK);
        out = BEARER.matcher(out).replaceAll("$1$2" + MASK);
        out = QUERY_PARAM.matcher(out).replaceAll("$1" + MASK);
        out = KEY_QUOTED_VALUE.matcher(out).replaceAll("$1$2$3" + MASK + "$3");
        out = KEY_VALUE.matcher(out).replaceAll("$1$2" + MASK);
        return out;
    }

    /**
     * Redacts the last {@code maxLines} lines, each truncated to {@code maxChars} characters
     * (after redaction), joined with newlines and indented.
     */
    public static String redactedTail(List<String> lines, int maxLines, int maxChars) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.size() - maxLines);
        for (int i = start; i < lines.size(); i++) {
            String l = redact(lines.get(i));
            if (l.length() > maxChars) {
                l = l.substring(0, maxChars) + "...";
            }
            sb.append("\n  [gatling] ").append(l);
        }
        return sb.toString();
    }
}
