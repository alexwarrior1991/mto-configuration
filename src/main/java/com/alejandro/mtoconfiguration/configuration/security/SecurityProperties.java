package com.alejandro.mtoconfiguration.configuration.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        boolean enabled,
        @NotBlank String clientId,
        @NotBlank String principalClaim,
        boolean audienceValidationEnabled,
        String requiredAudience,
        @Valid Cors cors
) {

    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            List<String> exposedHeaders,
            boolean allowCredentials,
            long maxAge
    ) {
    }
}
