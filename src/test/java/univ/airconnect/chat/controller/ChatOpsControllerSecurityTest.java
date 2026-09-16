package univ.airconnect.chat.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import univ.airconnect.global.security.stomp.StompOpsMonitor;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatOpsControllerSecurityTest {

    private StompOpsMonitor monitor;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        monitor = mock(StompOpsMonitor.class);
        var translation = new ExceptionTranslationFilter((request, response, error) -> response.sendError(401));
        translation.setAccessDeniedHandler((request, response, error) -> response.sendError(403));
        // Mirrors the dedicated ADMIN matcher in SecurityConfig without loading unrelated filters.
        var security = new FilterChainProxy(new DefaultSecurityFilterChain(
                AnyRequestMatcher.INSTANCE,
                new AnonymousAuthenticationFilter("chat-ops-fixture"),
                translation,
                new AuthorizationFilter(AuthorityAuthorizationManager.hasRole("ADMIN"))));
        mvc = MockMvcBuilders.standaloneSetup(new ChatOpsController(monitor)).addFilters(security).build();
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousAndOrdinaryUsersCannotReadGlobalStompMetrics() throws Exception {
        mvc.perform(get("/api/v1/chat/ops/stomp")).andExpect(status().isUnauthorized());
        authenticate("ROLE_USER");
        mvc.perform(get("/api/v1/chat/ops/stomp")).andExpect(status().isForbidden());
        verifyNoInteractions(monitor);
    }

    @Test
    void administratorCanReadGlobalStompMetrics() throws Exception {
        authenticate("ROLE_ADMIN");
        when(monitor.snapshot()).thenReturn(univ.airconnect.global.security.stomp.StompOpsSnapshot.builder().build());

        mvc.perform(get("/api/v1/chat/ops/stomp")).andExpect(status().isOk());

        verify(monitor).snapshot();
    }

    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "fixture", "unused", java.util.List.of(new SimpleGrantedAuthority(authority))));
    }
}
