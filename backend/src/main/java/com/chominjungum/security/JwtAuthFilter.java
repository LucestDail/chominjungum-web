package com.chominjungum.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /**
     * 게이트웨이(nginx)가 외부 요청에 HTTP Basic 을 요구하면 {@code Authorization} 헤더는
     * <b>Basic 전용</b>이 된다. 그 뒤에 앱 토큰까지 같은 헤더로 보내면 하나가 덮여 인증이 깨진다.
     * 그래서 홈랩 관례대로 앱 토큰은 별도 헤더로도 받는다.
     */
    public static final String TOKEN_HEADER = "X-Auth-Token";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null) {
            AuthPrincipal principal = jwtService.parse(token);
            if (principal != null) {
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    /** 전용 헤더를 먼저 보고, 없으면 표준 Bearer 를 본다(직결·LAN 사용). */
    private static String extractToken(HttpServletRequest request) {
        String dedicated = request.getHeader(TOKEN_HEADER);
        if (dedicated != null && !dedicated.isBlank()) {
            return dedicated.startsWith("Bearer ") ? dedicated.substring(7) : dedicated;
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
