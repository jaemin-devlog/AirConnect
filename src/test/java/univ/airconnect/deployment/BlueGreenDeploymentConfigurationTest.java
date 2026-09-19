package univ.airconnect.deployment;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlueGreenDeploymentConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void composeDefinesTwoIsolatedApplicationSlotsAndReadinessChecks() throws IOException {
        Map<String, Object> compose;
        try (var reader = Files.newBufferedReader(Path.of("docker-compose.yml"))) {
            compose = new Yaml().load(reader);
        }

        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> blue = (Map<String, Object>) services.get("airconnect-app");
        Map<String, Object> green = (Map<String, Object>) services.get("airconnect-app-green");
        Map<String, Object> caddy = (Map<String, Object>) services.get("caddy");
        Map<String, Object> commonApp = (Map<String, Object>) compose.get("x-airconnect-app");

        assertThat(blue.get("ports")).isEqualTo(List.of("127.0.0.1:8080:8080"));
        assertThat(green.get("ports")).isEqualTo(List.of("127.0.0.1:8081:8080"));
        assertThat(blue.get("hostname")).isEqualTo("airconnect-blue");
        assertThat(green.get("hostname")).isEqualTo("airconnect-green");
        assertThat(green.get("profiles")).isEqualTo(List.of("green"));

        Map<String, Object> environment = (Map<String, Object>) commonApp.get("environment");
        assertThat(environment.get("JPA_DDL_AUTO")).isEqualTo("${JPA_DDL_AUTO:-validate}");

        Map<String, Object> healthcheck = (Map<String, Object>) commonApp.get("healthcheck");
        assertThat((List<String>) healthcheck.get("test"))
                .anyMatch(value -> value.endsWith("/actuator/health/readiness"));

        assertThat((List<String>) caddy.get("volumes"))
                .contains("./deploy/runtime:/etc/caddy/runtime:ro");
    }

    @Test
    void caddyLoadsRuntimeSelectedUpstream() throws IOException {
        assertThat(Files.readString(Path.of("Caddyfile")))
                .contains("import /etc/caddy/runtime/active.caddy");
        assertThat(Files.readString(Path.of("deploy/active.caddy.example")))
                .contains("stream_close_delay 5m")
                .contains("/actuator/health/readiness");
    }
}
