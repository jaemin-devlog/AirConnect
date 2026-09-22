package univ.airconnect.global.security.jwt;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.response.ErrorBody;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;
import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_HEADER;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_EXCEPTION_ATTRIBUTE = "authException";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null
                && authorizationHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String accessToken = authorizationHeader.substring(7);

            try {
                jwtProvider.validateAccessToken(accessToken);
                Long userId = jwtProvider.getUserId(accessToken);
                User user = userRepository.findById(userId).orElse(null);

                if (user == null || isBlocked(user.getStatus())) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                CustomUserPrincipal principal = new CustomUserPrincipal(userId, user.getRole());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (AuthException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_EXCEPTION_ATTRIBUTE, e);
            } catch (DataAccessException e) {
                // Authentication cannot be trusted if its Redis/DB dependencies are unavailable.
                SecurityContextHolder.clearContext();
                String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", "5");
                if (traceId != null && !traceId.isBlank()) {
                    response.setHeader(TRACE_ID_HEADER, traceId);
                }
                objectMapper.writeValue(response.getWriter(), ApiResponse.fail(
                        new ErrorBody("COMMON-503", "잠시 후 다시 시도해 주세요.", 503, traceId, null), traceId));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(UserStatus status) {
        return status == UserStatus.DELETED
                || status == UserStatus.SUSPENDED
                || status == UserStatus.RESTRICTED;
    }
}
