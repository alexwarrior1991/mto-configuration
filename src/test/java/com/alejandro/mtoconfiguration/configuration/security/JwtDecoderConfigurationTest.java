package com.alejandro.mtoconfiguration.configuration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dos cosas que solo se rompen en despliegue: que el decodificador vaya a la red al construirse
 * —y tumbe el arranque si Keycloak no está todavía— y que la audiencia deje de comprobarse.
 */
class JwtDecoderConfigurationTest {

    private static final String ISSUER = "http://auth.mto.local:8082/realms/mto";
    private static final String AUDIENCIA = "mto-configuration-api";

    @Test
    @DisplayName("el JWK Set se deriva del emisor con la convención de Keycloak")
    void elJwkSetSeDerivaDelEmisor() {
        OAuth2ResourceServerProperties.Jwt jwt = new OAuth2ResourceServerProperties().getJwt();
        jwt.setIssuerUri(ISSUER);

        assertThat(SecurityConfiguration.resolveJwkSetUri(jwt))
                .isEqualTo(ISSUER + "/protocol/openid-connect/certs");
    }

    @Test
    @DisplayName("un jwk-set-uri explícito manda sobre la convención")
    void unJwkSetUriExplicitoManda() {
        OAuth2ResourceServerProperties.Jwt jwt = new OAuth2ResourceServerProperties().getJwt();
        jwt.setIssuerUri(ISSUER);
        jwt.setJwkSetUri("https://claves.interno/jwks.json");

        assertThat(SecurityConfiguration.resolveJwkSetUri(jwt)).isEqualTo("https://claves.interno/jwks.json");
    }

    /**
     * Es la regresión de S-06: con {@code JwtDecoders.fromIssuerLocation()} este bean hacía una
     * llamada HTTP al crearse, así que la aplicación no arrancaba si Keycloak no estaba listo.
     */
    @Test
    @DisplayName("construir el decodificador no toca la red: un emisor inalcanzable no impide arrancar")
    void construirElDecodificadorNoTocaLaRed() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        // Puerto 1: nada escucha ahí. Si hubiera descubrimiento, esto fallaría o se colgaría.
        properties.getJwt().setIssuerUri("http://127.0.0.1:1/realms/mto");

        JwtDecoder decoder = configuracion(true).jwtDecoder(properties);

        assertThat(decoder).isNotNull();
    }

    @Test
    @DisplayName("la audiencia correcta pasa y cualquier otra se rechaza")
    void laAudienciaSeComprueba() {
        JwtAudienceValidator validador = new JwtAudienceValidator(AUDIENCIA);

        assertThat(validador.validate(conAudiencia(List.of(AUDIENCIA))).hasErrors()).isFalse();
        assertThat(validador.validate(conAudiencia(List.of("otro-cliente", AUDIENCIA))).hasErrors()).isFalse();

        OAuth2TokenValidatorResult ajeno = validador.validate(conAudiencia(List.of("mto-stock-api")));
        assertThat(ajeno.hasErrors()).isTrue();
        assertThat(ajeno.getErrors()).anyMatch(error -> error.getDescription().contains(AUDIENCIA));
    }

    @Test
    @DisplayName("un token sin claim de audiencia se rechaza")
    void unTokenSinAudienciaSeRechaza() {
        JwtAudienceValidator validador = new JwtAudienceValidator(AUDIENCIA);

        assertThat(validador.validate(conAudiencia(null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("no se puede construir un validador con audiencia en blanco")
    void noSePuedeConstruirConAudienciaEnBlanco() {
        assertThatThrownBy(() -> new JwtAudienceValidator("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Jwt conAudiencia(List<String> audiencia) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("uuid");

        if (audiencia != null) {
            builder.audience(audiencia);
        }

        return builder.build();
    }

    private SecurityConfiguration configuracion(boolean validarAudiencia) {
        SecurityProperties propiedades = new SecurityProperties(
                AUDIENCIA, JwtClaimNames.PREFERRED_USERNAME, validarAudiencia, AUDIENCIA, false,
                new SecurityProperties.Cors(
                        List.of("http://localhost:4200"), List.of("GET"), List.of("Authorization"),
                        List.of(), false, 3600));

        return new SecurityConfiguration(
                propiedades,
                new KeycloakJwtAuthenticationConverter(propiedades),
                null,
                null);
    }
}
