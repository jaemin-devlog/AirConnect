package univ.airconnect.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 학교 이메일 인증은 더 이상 접근 제어가 아니라 선택 보상 마일스톤이다.
 * 인증 여부는 응답 필드와 보상 로직에서만 사용하고, API 접근 자체를 막지 않는다.
 */
@Component
public class VerifiedSchoolEmailFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }
}
