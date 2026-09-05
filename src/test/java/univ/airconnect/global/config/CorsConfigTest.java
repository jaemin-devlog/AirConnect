package univ.airconnect.global.config;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the CORS configuration without starting Spring Boot or loading external settings. */
class CorsConfigTest {

    private static final String NEW_ADMIN_ORIGIN = "https://airconnect-admin-a7781.web.app";
    private static final String PROBE_PATH = "/api/v1/admin/cors-probe";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://airconnect-admin.web.app",
            "https://airconnect-6e6c5.web.app",
            NEW_ADMIN_ORIGIN
    })
    void allowsEachExactDefaultOriginWithoutReplacingExistingSites(String origin) throws Exception {
        mockMvc("").perform(get(PROBE_PATH).header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @ParameterizedTest
    @MethodSource("preflightRequests")
    void allowsLoginAndAdminPreflightsWithRequiredRequestHeaders(String path, String method) throws Exception {
        mockMvc("").perform(options(path)
                        .header(HttpHeaders.ORIGIN, NEW_ADMIN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, NEW_ADMIN_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(method)))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://unrelated.example",
            "http://airconnect-admin-a7781.web.app",
            "https://airconnect-admin-a7781.firebaseapp.com",
            "https://airconnect-admin-a7781.web.app.unrelated.example",
            "https://airconnect-admin-a7781--preview.web.app",
            "http://localhost:5173"
    })
    void rejectsOriginsThatWereNotExplicitlyAllowed(String origin) throws Exception {
        mockMvc("").perform(options("/api/v1/auth/admin/login")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void keepsConfiguredAdditionalOriginsAdditiveAndDeduplicated() throws Exception {
        String extraOrigin = "https://additional-admin.example";
        String property = "  " + extraOrigin + ", " + NEW_ADMIN_ORIGIN + ", , " + extraOrigin + " ";
        CorsConfiguration configuration = configurations(property).get("/**");

        assertThat(configuration.getAllowedOriginPatterns()).containsExactly(
                "https://airconnect-admin.web.app",
                "https://airconnect-6e6c5.web.app",
                NEW_ADMIN_ORIGIN,
                extraOrigin
        );
        mockMvc(property).perform(get(PROBE_PATH).header(HttpHeaders.ORIGIN, extraOrigin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, extraOrigin));
    }

    private static Stream<Arguments> preflightRequests() {
        return Stream.of(
                Arguments.of("/api/v1/auth/admin/login", "POST"),
                Arguments.of("/api/v1/admin/users", "GET"),
                Arguments.of("/api/v1/admin/reports/1", "PATCH"),
                Arguments.of("/api/v1/admin/users/1/permanent", "DELETE")
        );
    }

    private static MockMvc mockMvc(String additionalOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        configurations(additionalOrigins).forEach(source::registerCorsConfiguration);
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new CorsFilter(source))
                .build();
    }

    private static Map<String, CorsConfiguration> configurations(String additionalOrigins) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOriginPatternsProperty", additionalOrigins);
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        config.addCorsMappings(registry);
        return registry.configurations();
    }

    private static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    @RestController
    private static class ProbeController {
        @GetMapping(PROBE_PATH)
        String probe() {
            return "ok";
        }
    }
}
