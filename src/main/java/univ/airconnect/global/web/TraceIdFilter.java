package univ.airconnect.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    // 요청/응답 헤더 이름
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    // request attribute key
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 클라이언트가 보낸 traceId 확인
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 없으면 서버에서 생성
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        // request attribute 저장 (컨트롤러 / ExceptionHandler에서 사용)
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);

        // 응답 헤더에도 넣어줌
        response.setHeader(TRACE_ID_HEADER, traceId);

        filterChain.doFilter(request, response);
    }
}