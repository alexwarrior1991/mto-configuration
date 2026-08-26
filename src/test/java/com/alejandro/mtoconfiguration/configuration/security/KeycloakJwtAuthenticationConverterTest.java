package com.alejandro.mtoconfiguration.configuration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El converter es lo único que traduce lo que dice Keycloak a lo que comprueban las reglas de la
 * cadena de filtros. Un fallo aquí no rompe nada visible: simplemente deja de conceder permisos, o
 * los concede de más.
 */
class KeycloakJwtAuthenticationConverterTest {

    private static final String CLIENT_ID = "mto-configuration-api";

    private final KeycloakJwtAuthenticationConverter converter =
            new KeycloakJwtAuthenticationConverter(propiedades());

    @Test
    @DisplayName("los roles de realm llegan como ROLE_ y como ROLE_REALM_")
    void losRolesDeRealmLleganConAmbosPrefijos() {
        Jwt jwt = jwt().claim(JwtClaimNames.REALM_ACCESS, Map.of("roles", List.of("mto-admin"))).build();

        assertThat(autoridades(jwt)).contains("ROLE_MTO_ADMIN", "ROLE_REALM_MTO_ADMIN");
    }

    @Test
    @DisplayName("los roles del cliente configurado llegan como ROLE_ y como ROLE_CLIENT_")
    void losRolesDelClienteLleganConAmbosPrefijos() {
        Jwt jwt = jwt()
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of(CLIENT_ID, Map.of("roles", List.of("config-read"))))
                .build();

        assertThat(autoridades(jwt))
                .contains("ROLE_" + SecurityRoles.CONFIG_READ, "ROLE_CLIENT_" + SecurityRoles.CONFIG_READ);
    }

    @Test
    @DisplayName("los roles de otros clientes del realm se ignoran")
    void losRolesDeOtrosClientesSeIgnoran() {
        Jwt jwt = jwt()
                .claim(JwtClaimNames.RESOURCE_ACCESS,
                        Map.of("mto-stock-api", Map.of("roles", List.of("config-delete"))))
                .build();

        assertThat(autoridades(jwt)).doesNotContain("ROLE_" + SecurityRoles.CONFIG_DELETE);
    }

    @Test
    @DisplayName("los scopes llegan con el prefijo SCOPE_")
    void losScopesLleganConSuPrefijo() {
        Jwt jwt = jwt().claim(JwtClaimNames.SCOPE, "openid profile mto-configuration").build();

        assertThat(autoridades(jwt)).contains("SCOPE_openid", "SCOPE_profile", "SCOPE_mto-configuration");
    }

    @Test
    @DisplayName("los permisos UMA llegan como PERMISSION_recurso:ambito")
    void losPermisosUmaLleganNormalizados() {
        Jwt jwt = jwt()
                .claim(JwtClaimNames.AUTHORIZATION, Map.of("permissions", List.of(
                        Map.of("rsname", "station", "scopes", List.of("read", "write")))))
                .build();

        assertThat(autoridades(jwt)).contains("PERMISSION_STATION:READ", "PERMISSION_STATION:WRITE");
    }

    @Test
    @DisplayName("un token sin roles no concede ninguna autoridad")
    void unTokenSinRolesNoConcedeNada() {
        assertThat(autoridades(jwt().build())).isEmpty();
    }

    @Test
    @DisplayName("el principal sale del claim configurado y cae al subject si falta")
    void elPrincipalSaleDelClaimConfigurado() {
        Jwt conUsuario = jwt().claim(JwtClaimNames.PREFERRED_USERNAME, "ana.perez").build();
        assertThat(converter.convert(conUsuario).getName()).isEqualTo("ana.perez");

        assertThat(converter.convert(jwt().build()).getName()).isEqualTo("subject-uuid");
    }

    /**
     * {@code normalize()} pasa a mayúsculas y sustituye guiones por guiones bajos, así que dos roles
     * que solo difieran en eso acaban siendo la misma autoridad. Se deja fijado porque condiciona
     * cómo hay que nombrar los roles en Keycloak.
     */
    @Test
    @DisplayName("guion y guion bajo colapsan en la misma autoridad")
    void guionYGuionBajoColapsanEnLaMismaAutoridad() {
        Jwt conGuion = jwt().claim(JwtClaimNames.REALM_ACCESS, Map.of("roles", List.of("mto-editor"))).build();
        Jwt conGuionBajo = jwt().claim(JwtClaimNames.REALM_ACCESS, Map.of("roles", List.of("MTO_EDITOR"))).build();

        assertThat(autoridades(conGuion)).isEqualTo(autoridades(conGuionBajo));
    }

    private List<String> autoridades(Jwt jwt) {
        AbstractAuthenticationToken token = converter.convert(jwt);
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    private Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("subject-uuid");
    }

    private SecurityProperties propiedades() {
        return new SecurityProperties(
                CLIENT_ID,
                JwtClaimNames.PREFERRED_USERNAME,
                true,
                CLIENT_ID,
                false,
                new SecurityProperties.Cors(
                        List.of("http://localhost:4200"), List.of("GET"), List.of("Authorization"),
                        List.of(), false, 3600));
    }
}
