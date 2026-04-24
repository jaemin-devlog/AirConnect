package univ.airconnect.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;

@Component
@RequiredArgsConstructor
public class AdminApiInterceptor implements HandlerInterceptor {

    static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final AdminProperties adminProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String configuredToken = adminProperties.getApiToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 API 토큰이 설정되지 않았습니다.");
        }

        String providedToken = request.getHeader(ADMIN_TOKEN_HEADER);
        if (providedToken == null || providedToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "관리자 API 토큰이 필요합니다.");
        }
        if (!configuredToken.equals(providedToken)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 API 토큰이 올바르지 않습니다.");
        }
        return true;
    }
}
