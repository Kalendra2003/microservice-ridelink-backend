package com.ridelink.driver_service.config;

import com.ridelink.driver_service.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Swagger UI and /v3/api-docs live outside /api, so they stay public.
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}