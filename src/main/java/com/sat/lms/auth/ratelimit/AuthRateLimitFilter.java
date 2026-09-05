package com.sat.lms.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final String SIGNUP_PATH = "/api/v1/auth/signup";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";

    private final AuthRateLimitProperties properties;
    private final AuthRateLimitStore store;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(AuthRateLimitProperties properties, AuthRateLimitStore store,
                               ClientIpResolver clientIpResolver, ObjectMapper objectMapper) {
        this.properties = properties;
        this.store = store;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = requestPath(request);
        return !LOGIN_PATH.equals(path) && !SIGNUP_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean login = LOGIN_PATH.equals(requestPath(request));
        AuthRateLimitProperties.Limit limit = login ? properties.login() : properties.signup();
        AuthRateLimitStore.Endpoint endpoint = login
                ? AuthRateLimitStore.Endpoint.LOGIN : AuthRateLimitStore.Endpoint.SIGNUP;
        AuthRateLimitStore.Result result = store.consume(endpoint, clientIpResolver.resolve(request), limit);

        response.setHeader("X-RateLimit-Limit", Long.toString(result.limit()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(result.remaining()));
        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(result.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(TOO_MANY_REQUESTS_MESSAGE));
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }
}
