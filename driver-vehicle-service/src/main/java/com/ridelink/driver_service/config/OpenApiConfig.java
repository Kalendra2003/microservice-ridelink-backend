package com.ridelink.driver_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI driverServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink - Driver & Vehicle Service")
                        .version("1.0.0")
                        .description("Driver operational profiles, vehicles, availability, service area, "
                                + "simulated location and eligible-driver lookup. "
                                + "Authenticate with a JWT issued by the Account Service."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}