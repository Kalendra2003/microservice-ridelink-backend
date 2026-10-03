package com.ridelink.driver_service.security;

import java.util.Arrays;

import com.ridelink.driver_service.exception.ForbiddenException;
import com.ridelink.driver_service.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates every /api request from its Bearer token and enforces @RequireRole.
 * Exceptions thrown here are rendered by GlobalExceptionHandler, so errors share one format.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTRIBUTE = "authenticatedUser";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() == BEARER_PREFIX.length()) {
            throw new UnauthorizedException("Missing or malformed Authorization header (expected 'Bearer <token>')");
        }
        AuthenticatedUser user = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim());
        request.setAttribute(USER_ATTRIBUTE, user);

        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required != null && !Arrays.asList(required.value()).contains(user.role())) {
            throw new ForbiddenException("Role " + user.role() + " is not allowed to perform this operation");
        }
        return true;
    }
}