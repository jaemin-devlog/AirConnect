package univ.airconnect.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.repository.UserRepository;

import java.io.IOException;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Component
@RequiredArgsConstructor
public class VerifiedSchoolEmailFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isAllowedForUnverifiedUser(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null || user.isAdmin() || user.hasVerifiedSchoolEmail()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeForbiddenResponse(request, response);
    }

    private boolean isAllowedForUnverifiedUser(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.startsWith("/api/v1/auth/")) {
            return true;
        }
        if (uri.startsWith("/api/v1/verification/")) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && uri.startsWith("/api/v1/users/profile-images/")) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/users/me".equals(uri)) {
            return true;
        }
        if (HttpMethod.DELETE.matches(method) && "/api/v1/users/me".equals(uri)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/users/sign-up".equals(uri)) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/users/profile".equals(uri)) {
            return true;
        }
        if (HttpMethod.PATCH.matches(method) && "/api/v1/users/profile".equals(uri)) {
            return true;
        }
        if (HttpMethod.PATCH.matches(method) && "/api/v1/users/me/nickname".equals(uri)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/users/profile-image".equals(uri)) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/users/school-consent".equals(uri)) {
            return true;
        }
        return HttpMethod.PUT.matches(method) && "/api/v1/users/school-consent".equals(uri);
    }

    private void writeForbiddenResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        UserErrorCode errorCode = UserErrorCode.SCHOOL_EMAIL_VERIFICATION_REQUIRED;
        ErrorBody body = new ErrorBody(
                errorCode.getCode(),
                errorCode.getMessage(),
                errorCode.getHttpStatus().value(),
                traceId,
                null
        );

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(body, traceId));
    }
}
