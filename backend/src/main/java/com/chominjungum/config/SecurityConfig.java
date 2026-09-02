package com.chominjungum.config;

import com.chominjungum.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${chominjungum.cors.allowed-origins:http://localhost:5180}") List<String> allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 토큰이 없으면 401, 있는데 권한이 모자라면 403 (기본값은 둘 다 403)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다.")))
                .authorizeHttpRequests(auth -> auth
                        // 공개: 가입·로그인·참여, 한글 분해(정답 노출 없음), 헬스
                        .requestMatchers("/api/auth/**", "/api/hangul/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 응시는 학생 토큰
                        .requestMatchers("/api/exam/**").hasRole("STUDENT")
                        // 나머지 관리 API 는 교사
                        .requestMatchers("/api/**").hasRole("TEACHER")
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
