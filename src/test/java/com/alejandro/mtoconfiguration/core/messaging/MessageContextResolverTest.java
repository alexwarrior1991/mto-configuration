package com.alejandro.mtoconfiguration.core.messaging;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.configuration.security.JwtClaimNames;
import com.alejandro.mtoconfiguration.configuration.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El sobre dice quien hizo el cambio y bajo que identificador, y lo dice el emisor porque es el
 * unico que tiene el token y la peticion delante. Lo que se fija aqui es la clasificacion: una
 * cuenta de servicio no es una persona, y un proceso sin usuario se declara como tal en vez de
 * callarse.
 */
class MessageContextResolverTest {

    private static final String SUBJECT = "6f1b1c8e-0000-4000-8000-000000000001";

    private final MessageContextResolver resolver = new MessageContextResolver(new CurrentUserService());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @Nested
    @DisplayName("el actor")
    class Actor {

        @Test
        @DisplayName("sin nadie autenticado es SYSTEM, dicho explicitamente y sin nombre")
        void sinUsuarioEsSystem() {
            assertThat(resolver.currentActor())
                    .isEqualTo(new MessageActor(null, null, MessageActorKind.SYSTEM));
        }

        @Test
        @DisplayName("una persona con token es PERSON, con su sub y su preferred_username")
        void unaPersonaEsPerson() {
            autenticarConJwt("ana.perez");

            assertThat(resolver.currentActor())
                    .isEqualTo(new MessageActor(SUBJECT, "ana.perez", MessageActorKind.PERSON));
        }

        @Test
        @DisplayName("la cuenta de servicio de otro servicio es SERVICE, por el prefijo con el que Keycloak la nombra")
        void unaCuentaDeServicioEsService() {
            autenticarConJwt("service-account-mto-users-svc");

            assertThat(resolver.currentActor())
                    .isEqualTo(new MessageActor(SUBJECT, "service-account-mto-users-svc", MessageActorKind.SERVICE));
        }

        @Test
        @DisplayName("una autenticacion que no es un JWT sigue siendo una persona, con su nombre y sin sub")
        void unaAutenticacionSinJwtEsPersonConSuNombre() {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new UsernamePasswordAuthenticationToken(
                    "ana.perez", "n/a", AuthorityUtils.createAuthorityList("ROLE_CONFIG_READ")));
            SecurityContextHolder.setContext(contexto);

            assertThat(resolver.currentActor())
                    .isEqualTo(new MessageActor(null, "ana.perez", MessageActorKind.PERSON));
        }
    }

    @Nested
    @DisplayName("el correlationId")
    class Correlation {

        @Test
        @DisplayName("es el que CorrelationIdFilter o un trabajo dejaron en el MDC")
        void esElDelMdc() {
            MDC.put(CorrelationIdFilter.MDC_KEY, "8c3b8c1a-1111-4222-8333-444444444444");

            assertThat(resolver.currentCorrelationId()).isEqualTo("8c3b8c1a-1111-4222-8333-444444444444");
        }

        @Test
        @DisplayName("fuera de una peticion y de un trabajo no hay ninguno, y se dice con null en vez de inventarlo")
        void sinMdcEsNulo() {
            assertThat(resolver.currentCorrelationId()).isNull();

            MDC.put(CorrelationIdFilter.MDC_KEY, "   ");

            assertThat(resolver.currentCorrelationId()).isNull();
        }
    }

    private static void autenticarConJwt(String username) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUBJECT)
                .claim(JwtClaimNames.PREFERRED_USERNAME, username)
                .build();

        Authentication authentication = new JwtAuthenticationToken(
                jwt, AuthorityUtils.createAuthorityList("ROLE_CONFIG_READ"), username);

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(authentication);
        SecurityContextHolder.setContext(contexto);
    }
}
