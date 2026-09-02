package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
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
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    private static final String API = ConfigurationApiPaths.BASE_PATH;
    private static final String ASYNC_API = ConfigurationApiPaths.ASYNC_BASE_PATH;

    /**
     * Consultas que viajan por POST porque llevan cuerpo, no porque escriban nada. Se enumeran
     * aparte para que no caigan bajo la regla de escritura: pedir permiso de escritura para buscar
     * empujaría a dar {@code config-write} a perfiles de solo lectura.
     * <p>
     * Los patrones llevan un único comodín de segmento ({@code /*}) en lugar de {@code /**} en
     * mitad de la ruta: {@code PathPattern}, el motor que usa Spring Security, solo admite
     * {@code /**} al final. Por eso las variantes síncrona y asíncrona van explícitas.
     */
    private static final String[] QUERY_BY_POST = {
            API + "/*/search", API + "/*/filter",
            ASYNC_API + "/*/search", ASYNC_API + "/*/filter",
            // Lanzar una exportacion viaja por POST porque crea un trabajo, pero lo que expone son
            // los mismos datos que el {@code GET .../export} de siempre, que pide lectura. Pedir
            // permiso de escritura para obtener un CSV obligaria a dar {@code config-write} a
            // perfiles que solo consultan.
            API + "/*/jobs/export"
    };

    /** Cargas masivas: un solo error afecta a miles de registros, así que llevan permiso propio. */
    private static final String[] BULK = {
            API + "/*/bulk",
            ASYNC_API + "/*/bulk",
            // Las cargas masivas lanzadas como trabajo escriben exactamente lo mismo que las
            // sincronas, asi que llevan el mismo permiso. Sin estas dos entradas caerian en la
            // regla general de POST/PUT y bastaria {@code config-write} para cargar en masa,
            // que es justo la distincion que BULK existe para mantener.
            API + "/*/jobs/bulk-create",
            API + "/*/jobs/bulk-update",
            // La importacion del catalogo maestro escribe sobre TODAS las tablas LOV de una
            // vez, asi que lleva el mismo permiso que el resto de cargas masivas. Sin esta
            // entrada caeria en la regla general de POST y bastaria config-write, que es
            // justo la distincion que BULK existe para mantener.
            API + "/*/jobs/import"
    };

    private static final String[] API_DOCS = {
            "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
            "/swagger-ui/**", "/swagger-ui.html"
    };

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
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    authorize.requestMatchers(
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info"
                    ).permitAll();

                    // La documentación describe la superficie completa de la API. En un entorno
                    // desplegado es el mejor mapa posible para quien busque un hueco, así que se
                    // abre solo donde la configuración lo pide.
                    if (securityProperties.exposeApiDocs()) {
                        authorize.requestMatchers(API_DOCS).permitAll();
                    }

                    // Todo Actuator cerrado salvo health e info. En master estas rutas iban
                    // enumeradas una a una (/actuator/prometheus, /actuator/outbox, que publica
                    // el estado del outbox y el redrive de los mensajes FAILED: explotación, no
                    // negocio); la regla general las cubre y además no deja fuera los endpoints
                    // que se expongan más adelante.
                    // Lo que modifica va antes que la regla general de Actuator y con su propio
                    // permiso. En Actuator las @WriteOperation viajan por POST y las
                    // @DeleteOperation por DELETE; el resto es lectura.
                    authorize.requestMatchers(HttpMethod.POST, "/actuator/**").hasRole(SecurityRoles.OPS_WRITE);
                    authorize.requestMatchers(HttpMethod.DELETE, "/actuator/**").hasRole(SecurityRoles.OPS_WRITE);
                    authorize.requestMatchers("/actuator/**").hasRole(SecurityRoles.OPS_METRICS);

                    // El orden importa: cada petición se resuelve con la primera regla que encaja,
                    // así que lo específico (consultas por POST, cargas masivas) va antes que la
                    // regla general del verbo.
                    authorize.requestMatchers(HttpMethod.GET, API + "/**").hasRole(SecurityRoles.CONFIG_READ);
                    // HEAD lo sirve el mismo handler que GET y revela si un recurso existe, así que
                    // no puede quedarse en el 'anyRequest().authenticated()' del final.
                    authorize.requestMatchers(HttpMethod.HEAD, API + "/**").hasRole(SecurityRoles.CONFIG_READ);
                    authorize.requestMatchers(HttpMethod.POST, QUERY_BY_POST).hasRole(SecurityRoles.CONFIG_READ);
                    authorize.requestMatchers(HttpMethod.POST, BULK).hasRole(SecurityRoles.CONFIG_IMPORT);
                    authorize.requestMatchers(HttpMethod.PUT, BULK).hasRole(SecurityRoles.CONFIG_IMPORT);
                    // El seguimiento y la descarga de un trabajo (GET .../jobs/{id} y
                    // .../jobs/{id}/file) ya los cubre la regla de GET de mas arriba, que pide
                    // lectura: son consultas del estado y del resultado, no operaciones nuevas.
                    authorize.requestMatchers(HttpMethod.POST, API + "/**").hasRole(SecurityRoles.CONFIG_WRITE);
                    authorize.requestMatchers(HttpMethod.PUT, API + "/**").hasRole(SecurityRoles.CONFIG_WRITE);
                    authorize.requestMatchers(HttpMethod.PATCH, API + "/**").hasRole(SecurityRoles.CONFIG_WRITE);
                    authorize.requestMatchers(HttpMethod.DELETE, API + "/**").hasRole(SecurityRoles.CONFIG_DELETE);

                    authorize.anyRequest().authenticated();
                })
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

    /**
     * Se construye con el JWK Set en lugar de con el descubrimiento por issuer: {@code
     * JwtDecoders.fromIssuerLocation()} hace una llamada HTTP bloqueante al crear el bean, de modo
     * que la aplicación no arrancaba si Keycloak todavía no servía el {@code
     * .well-known/openid-configuration}. Con el JWK Set la descarga es perezosa —la primera vez que
     * llega un token— y un reinicio simultáneo de los dos servicios deja de ser un fallo de
     * arranque.
     */
    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        OAuth2ResourceServerProperties.Jwt jwtProperties = properties.getJwt();
        String issuerUri = jwtProperties.getIssuerUri();

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(resolveJwkSetUri(jwtProperties))
                .build();

        // Se parte del validador por defecto en vez de reemplazarlo: incluye la comprobación de
        // emisor y de vigencia, y hereda las que Spring Security añada en versiones futuras.
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator()
        ));

        return jwtDecoder;
    }

    /**
     * Keycloak publica el JWK Set en una ruta fija bajo el realm. Se respeta {@code jwk-set-uri} si
     * está configurado, para no atar la aplicación a esa convención.
     */
    static String resolveJwkSetUri(OAuth2ResourceServerProperties.Jwt jwtProperties) {
        if (StringUtils.hasText(jwtProperties.getJwkSetUri())) {
            return jwtProperties.getJwkSetUri();
        }

        return jwtProperties.getIssuerUri() + "/protocol/openid-connect/certs";
    }

    /**
     * Que {@code required-audience} esté relleno lo garantiza {@link SecurityProperties} en el
     * arranque, así que aquí no hay ninguna rama que deje pasar el token por falta de
     * configuración: o se valida la audiencia, o se ha desactivado de forma explícita.
     */
    private OAuth2TokenValidator<Jwt> audienceValidator() {
        if (!securityProperties.audienceValidationEnabled()) {
            return jwt -> OAuth2TokenValidatorResult.success();
        }

        return new JwtAudienceValidator(securityProperties.requiredAudience());
    }

}
