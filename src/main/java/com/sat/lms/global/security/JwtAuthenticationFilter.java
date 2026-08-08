package com.sat.lms.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        /*
         * 토큰이 있고 정상적인 경우에만
         * SecurityContext에 인증 정보를 등록한다.
         */
        if (token != null && jwtTokenProvider.validateToken(token)) {

            Long memberId =
                    jwtTokenProvider.getMemberId(token);

            String role =
                    jwtTokenProvider.getRole(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            memberId,
                            null,
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + role
                                    )
                            )
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
        }

        /*
         * 중요:
         *
         * 토큰이 없거나
         * 만료됐거나
         * 변조됐더라도
         *
         * 여기서 직접 401을 반환하지 않는다.
         *
         * SecurityConfig가
         * permitAll인지 인증 필수인지 판단하게 한다.
         */
        filterChain.doFilter(request, response);
    }

    private String resolveToken(
            HttpServletRequest request
    ) {

        String authorization =
                request.getHeader(AUTHORIZATION_HEADER);

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)) {

            return null;
        }

        return authorization.substring(
                BEARER_PREFIX.length()
        );
    }
}