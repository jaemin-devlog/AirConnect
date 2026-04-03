package univ.airconnect.analytics.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import univ.airconnect.analytics.service.UserActivityService;
import univ.airconnect.global.security.principal.CustomUserPrincipal;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class UserActivityFilter extends OncePerRequestFilter {

    private final UserActivityService userActivityService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            userActivityService.touch(principal.getUserId());
        }

        filterChain.doFilter(request, response);
    }
}
