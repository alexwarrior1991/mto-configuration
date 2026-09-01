package com.alejandro.mtoconfiguration.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lectura del token del usuario en curso.
 *
 * <p>No es una utilidad decorativa: {@code getUserId()}, {@code getUsername()} y
 * {@code getCurrentRoles()} alimentan el {@code searchParams()} de los ocho servicios de
 * infraestructura, y de ahi viajan al predicado de la busqueda por criteria. Devolver de mas o de
 * menos aqui cambia <b>lo que ve cada usuario</b>, y lo hace en silencio: no hay error, hay
 * resultados distintos.
 *
 * <p>Lo otro que se fija son los valores por defecto sin autenticacion. Muchos de estos metodos se
 * invocan tambien fuera de una peticion HTTP —desde un trabajo en segundo plano, por ejemplo— y
 * ahi no hay token: tienen que degradar, no reventar.
 */
class PermissionUtilsTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("u-123")
                .issuer("https://keycloak.local/realms/mto")
                .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
                .claim("preferred_username", "ana")
                .claim("email", "ana@example.com")
                .claim("scope", "profile email mto:read")
                .claim("realm_access", Map.of("roles", List.of("mto-editor", "mto-admin")))
                .claim("resource_access", Map.of(
                        "mto-frontend", Map.of("roles", List.of("ui-user"))))
                .build();
    }

    private static void autenticarCon(Jwt jwt, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Nested
    @DisplayName("Con un token valido")
    class ConToken {

        @Test
        @DisplayName("identifica al usuario por subject, username y email")
        void identidad() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getUserId()).isEqualTo("u-123");
            assertThat(PermissionUtils.getUsername()).isEqualTo("ana");
            assertThat(PermissionUtils.getEmail()).isEqualTo("ana@example.com");
            assertThat(PermissionUtils.isAuthenticated()).isTrue();
            assertThat(PermissionUtils.getJwt()).isPresent();
        }

        @Test
        @DisplayName("el realm sale del ultimo segmento del emisor")
        void nombreDelRealm() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getRealmName()).isEqualTo("mto");
        }

        @Test
        @DisplayName("los roles de realm salen de realm_access.roles")
        void rolesDeRealm() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getRealmRoles())
                    .containsExactlyInAnyOrder("mto-editor", "mto-admin");
            assertThat(PermissionUtils.hasRealmRole("mto-admin")).isTrue();
            assertThat(PermissionUtils.hasRealmRole("mto-viewer")).isFalse();
            assertThat(PermissionUtils.hasAnyRealmRole("mto-viewer", "mto-editor")).isTrue();
            assertThat(PermissionUtils.hasAnyRealmRole("mto-viewer")).isFalse();
        }

        @Test
        @DisplayName("los roles de cliente salen de resource_access, por cliente")
        void rolesDeCliente() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getClientRoles("mto-frontend")).containsExactly("ui-user");
            assertThat(PermissionUtils.getClientRoles("otro-cliente")).isEmpty();
            assertThat(PermissionUtils.hasClientRole("mto-frontend", "ui-user")).isTrue();
            assertThat(PermissionUtils.hasAnyClientRole("mto-frontend", "nope", "ui-user")).isTrue();
            assertThat(PermissionUtils.hasAnyClientRole("mto-frontend", "nope")).isFalse();
        }

        @Test
        @DisplayName("los scopes se parten por espacios")
        void scopes() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getScopes()).containsExactly("profile", "email", "mto:read");
            assertThat(PermissionUtils.hasScope("mto:read")).isTrue();
            assertThat(PermissionUtils.hasScope("mto:write")).isFalse();
        }

        @Test
        @DisplayName("las authorities salen del Authentication, no del token")
        void authorities() {
            autenticarCon(jwt(), "ROLE_CONFIG_READ", "ROLE_CONFIG_WRITE");

            assertThat(PermissionUtils.getAuthorities())
                    .containsExactlyInAnyOrder("ROLE_CONFIG_READ", "ROLE_CONFIG_WRITE");
            assertThat(PermissionUtils.hasAuthority("ROLE_CONFIG_READ")).isTrue();
            assertThat(PermissionUtils.hasAuthority("ROLE_LOV_MANAGE")).isFalse();
        }

        @Test
        @DisplayName("los claims completos estan disponibles")
        void claims() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getCustomAttributes())
                    .containsKeys("preferred_username", "realm_access", "scope");
        }
    }

    @Nested
    @DisplayName("Sin autenticacion")
    class SinToken {

        @Test
        @DisplayName("la identidad degrada a valores por defecto, sin lanzar")
        void identidadPorDefecto() {
            assertThat(PermissionUtils.getUserId()).isNull();
            assertThat(PermissionUtils.getEmail()).isNull();
            assertThat(PermissionUtils.getRealmName()).isNull();
            assertThat(PermissionUtils.getJwt()).isEmpty();
            assertThat(PermissionUtils.isAuthenticated()).isFalse();
        }

        @Test
        @DisplayName("el username por defecto es 'anonymous', no null")
        void usernamePorDefecto() {
            // searchParams() lo mete en el mapa de filtros: un null ahi cambiaria el predicado.
            assertThat(PermissionUtils.getUsername()).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("las colecciones degradan a vacio, nunca a null")
        void coleccionesVacias() {
            assertThat(PermissionUtils.getRealmRoles()).isEmpty();
            assertThat(PermissionUtils.getCurrentRoles()).isEmpty();
            assertThat(PermissionUtils.getClientRoles("mto-frontend")).isEmpty();
            assertThat(PermissionUtils.getScopes()).isEmpty();
            assertThat(PermissionUtils.getAuthorities()).isEmpty();
            assertThat(PermissionUtils.getCustomAttributes()).isEmpty();
        }

        @Test
        @DisplayName("un usuario anonimo no cuenta como autenticado")
        void anonimo() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

            assertThat(PermissionUtils.isAuthenticated()).isFalse();
        }
    }

    @Nested
    @DisplayName("Claims malformados")
    class ClaimsMalformados {

        @Test
        @DisplayName("un realm_access sin roles no rompe")
        void realmAccessSinRoles() {
            autenticarCon(Jwt.withTokenValue("t").header("alg", "none")
                    .claim("realm_access", Map.of("otro", "valor"))
                    .build());

            assertThat(PermissionUtils.getRealmRoles()).isEmpty();
            assertThat(PermissionUtils.getCurrentRoles()).isEmpty();
        }

        @Test
        @DisplayName("un roles que no es lista se ignora")
        void rolesQueNoEsLista() {
            autenticarCon(Jwt.withTokenValue("t").header("alg", "none")
                    .claim("realm_access", Map.of("roles", "mto-admin"))
                    .build());

            assertThat(PermissionUtils.getRealmRoles()).isEmpty();
        }

        @Test
        @DisplayName("los elementos que no son cadena o estan en blanco se descartan")
        void elementosNoTextuales() {
            autenticarCon(Jwt.withTokenValue("t").header("alg", "none")
                    .claim("realm_access", Map.of("roles", Arrays.asList("mto-admin", 42, "  ", "mto-editor")))
                    .build());

            assertThat(PermissionUtils.getRealmRoles())
                    .containsExactlyInAnyOrder("mto-admin", "mto-editor");
        }

        @Test
        @DisplayName("un scope vacio no produce una entrada en blanco")
        void scopeVacio() {
            autenticarCon(Jwt.withTokenValue("t").header("alg", "none")
                    .claim("scope", "   ")
                    .build());

            assertThat(PermissionUtils.getScopes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Las dos formas de leer los roles de realm")
    class DosFormasDeLeerRoles {

        @Test
        @DisplayName("con JwtAuthenticationToken las dos coinciden")
        void coincidenConJwtAuthenticationToken() {
            autenticarCon(jwt());

            assertThat(PermissionUtils.getCurrentRoles())
                    .isEqualTo(PermissionUtils.getRealmRoles());
        }

        @Test
        @DisplayName("si el principal es un Jwt pero el Authentication no, solo getCurrentRoles lo ve")
        void divergenciaConOtroAuthentication() {
            // getCurrentRoles mira tambien el principal; getRealmRoles exige un
            // JwtAuthenticationToken. La diferencia no importa hoy porque la cadena de filtros
            // siempre produce un JwtAuthenticationToken, pero queda escrita: si alguien envuelve
            // la autenticacion de otra forma, searchParams() dejaria de ver los roles por un lado
            // y los seguiria viendo por el otro.
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(jwt(), null, List.of()));

            assertThat(PermissionUtils.getCurrentRoles())
                    .containsExactlyInAnyOrder("mto-editor", "mto-admin");
            assertThat(PermissionUtils.getRealmRoles()).isEmpty();
        }
    }
}
