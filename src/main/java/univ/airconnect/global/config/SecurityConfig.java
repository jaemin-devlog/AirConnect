package univ.airconnect.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import univ.airconnect.analytics.web.UserActivityFilter;
import univ.airconnect.global.security.jwt.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserActivityFilter userActivityFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth

                        // 인증 관련
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // 이메일 인증
                        .requestMatchers("/api/v1/verification/**").permitAll()

                        .requestMatchers("/api/v1/statistics/**").permitAll()

                        // Admin API is protected by a dedicated admin token interceptor.
                        .requestMatchers("/api/v1/admin/**").permitAll()

                        // 프로필 이미지 조회
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/profile-images/**").permitAll()

                        // WebSocket
                        .requestMatchers("/ws-stomp/**").permitAll()

                        // IAP webhook
                        .requestMatchers("/api/v1/iap/ios/notifications", "/api/v1/iap/android/notifications").permitAll()

                        // AdMob SSV callback
                        .requestMatchers("/api/v1/ads/rewards/callback/admob").permitAll()


                        // Swagger
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()

                        // Preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(userActivityFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
