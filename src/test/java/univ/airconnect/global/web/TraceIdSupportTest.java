package univ.airconnect.global.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdSupportTest {

    @Test
    void resolveOrGenerate_keepsSafeTraceId() {
        String traceId = TraceIdSupport.resolveOrGenerate("trace-123_ABC");

        assertThat(traceId).isEqualTo("trace-123_ABC");
    }

    @Test
    void resolveOrGenerate_replacesUnsafeTraceId() {
        String traceId = TraceIdSupport.resolveOrGenerate("bad trace id with spaces");

        assertThat(traceId).isNotEqualTo("bad trace id with spaces");
        assertThat(traceId).matches("^[A-Za-z0-9._-]{1,128}$");
    }
}
