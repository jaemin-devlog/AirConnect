package univ.airconnect.admin.insights;

import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminInsightsSecurityTest {
    AdminInsightsService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(AdminInsightsService.class);
        var translation=new ExceptionTranslationFilter((request,response,error)->response.sendError(401));
        translation.setAccessDeniedHandler((request,response,error)->response.sendError(403));
        // Same ADMIN matcher rule as production, isolated from credentials and Redis.
        var security=new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("insights-fixture"),translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        mvc=MockMvcBuilders.standaloneSetup(new AdminInsightsController(service)).addFilters(security).build();
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void anonymousCannotReadEitherEndpoint() throws Exception {
        for(String path:new String[]{"/api/v1/admin/insights","/api/v1/admin/insights/members"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void ordinaryMemberCannotReadEitherEndpoint() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture","unused",java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        for(String path:new String[]{"/api/v1/admin/insights","/api/v1/admin/insights/members"}) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture","unused",java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void administratorGetsNoStoreEnvelope() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture","unused",java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(service.overview(null,null)).thenReturn(Map.of("timezone","Asia/Seoul"));
        mvc.perform(get("/api/v1/admin/insights")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture","unused",java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        mvc.perform(get("/api/v1/admin/insights?from=not-a-date")).andExpect(status().isBadRequest());
    }
}
