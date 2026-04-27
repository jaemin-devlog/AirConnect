package univ.airconnect.global.web;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceIdSupport {

    private static final Pattern SAFE_TRACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private TraceIdSupport() {
    }

    public static String resolveOrGenerate(String candidate) {
        if (candidate == null) {
            return UUID.randomUUID().toString();
        }

        String normalized = candidate.trim();
        if (SAFE_TRACE_ID_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }

        return UUID.randomUUID().toString();
    }
}
