package com.alejandro.mtoconfiguration.configuration.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnProperty(prefix = "app.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfiguration {

    private final SecurityProperties securityProperties;
    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfiguration(
            SecurityProperties securityProperties,
            KeycloakJwtAuthenticationConverter jwtAuthenticationConverter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.securityProperties = securityProperties;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandlingConfigurer ->
                        exceptionHandlingConfigurer.authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasAnyRole("ADMIN", "OPS")
                        // Estado del outbox y redrive de los mensajes FAILED: explotacion, no negocio.
                        .requestMatchers("/actuator/outbox", "/actuator/outbox/**").hasAnyRole("ADMIN", "OPS")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        SecurityProperties.Cors corsProperties = securityProperties.cors();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods());
        configuration.setAllowedHeaders(corsProperties.allowedHeaders());
        configuration.setExposedHeaders(corsProperties.exposedHeaders());
        configuration.setAllowCredentials(corsProperties.allowCredentials());
        configuration.setMaxAge(corsProperties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        String issuerUri = properties.getJwt().getIssuerUri();
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (!securityProperties.audienceValidationEnabled()) {
                return OAuth2TokenValidatorResult.success();
            }

            String requiredAudience = securityProperties.requiredAudience();
            if (requiredAudience == null || requiredAudience.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }

            if (jwt.getAudience() != null && jwt.getAudience().contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "The required audience is missing: " + requiredAudience,
                            null
                    )
            );
        };

        DelegatingOAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(timestampValidator, issuerValidator, audienceValidator);

        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

}
