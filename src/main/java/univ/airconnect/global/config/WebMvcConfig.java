package univ.airconnect.global.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import univ.airconnect.admin.AdminApiAuditInterceptor;
import univ.airconnect.analytics.web.ApiRequestLoggingInterceptor;
import univ.airconnect.global.security.resolver.CurrentUserIdArgumentResolver;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final AdminApiAuditInterceptor adminApiAuditInterceptor;
    private final ApiRequestLoggingInterceptor apiRequestLoggingInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRequestLoggingInterceptor)
                .addPathPatterns("/api/v1/**");
        registry.addInterceptor(adminApiAuditInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /api/** 경로는 API 컨트롤러에서 처리하므로, 
        // 정적 리소스 핸들러의 기본 매칭에서 제외
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/")
                .setCachePeriod(31536000); // 1년 캐시
    }
}
