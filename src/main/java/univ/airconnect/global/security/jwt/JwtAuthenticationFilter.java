package univ.airconnect.global.security.jwt;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import univ.airconnect.auth.exception.AuthException;
import univ.airconnect.global.security.principal.CustomUserPrincipal;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_EXCEPTION_ATTRIBUTE = "authException";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

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
