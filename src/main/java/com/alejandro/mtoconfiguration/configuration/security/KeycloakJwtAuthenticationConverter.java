package com.alejandro.mtoconfiguration.configuration.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SecurityProperties securityProperties;

    public KeycloakJwtAuthenticationConverter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        authorities.addAll(extractScopes(jwt));
        authorities.addAll(extractRealmRoles(jwt));
        authorities.addAll(extractClientRoles(jwt, securityProperties.clientId()));
        authorities.addAll(extractAuthorizationPermissions(jwt));

        String principalName = resolvePrincipalName(jwt);

        return new JwtAuthenticationToken(jwt, authorities, principalName);

    }

    private Collection<GrantedAuthority> extractScopes(Jwt jwt) {
        String scope = jwt.getClaimAsString(JwtClaimNames.SCOPE);
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }

        return Stream.of(scope.split(" "))
                .filter(value -> value != null && !value.isBlank())
                .map(value -> SecurityAuthorityPrefixes.SCOPE_PREFIX + value)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Los roles de realm se emiten <b>solo</b> con el prefijo {@code ROLE_REALM_}, nunca con
     * {@code ROLE_} a secas.
     *
     * <p>Emitir ambos hacía que un rol de realm y uno de cliente que se llamaran igual acabaran en
     * la misma autoridad: quien tuviera el de realm pasaba una comprobación pensada para el de
     * cliente. Y como quien administra el realm no es necesariamente quien escribe el código,
     * bastaba con crear allí un rol llamado igual que un permiso para concedérselo a cualquiera.</p>
     *
     * <p>La separación encaja con el modelo: los permisos que comprueba el código son roles de
     * cliente, y los roles de realm son perfiles de negocio <em>compuestos</em> que los agrupan.
     * Keycloak expande los compuestos al emitir el token, así que un usuario con el perfil de realm
     * {@code mto-editor} ya llega con {@code config-read} y {@code config-write} dentro de
     * {@code resource_access}. Comprobar un perfil sigue siendo posible, pero hay que nombrarlo:
     * {@code hasRole("REALM_MTO_EDITOR")}.</p>
     */
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(JwtClaimNames.REALM_ACCESS);

        if (realmAccess == null) {
            return Set.of();
        }

        return extractRoles(realmAccess).stream()
                .map(role -> SecurityAuthorityPrefixes.REALM_ROLE_PREFIX + normalize(role))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Collection<GrantedAuthority> extractClientRoles(Jwt jwt, String clientId) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(JwtClaimNames.RESOURCE_ACCESS);
        if (resourceAccess == null || clientId == null || clientId.isBlank()) {
            return Set.of();
        }

        Object clientAccess = resourceAccess.get(clientId);
        if (!(clientAccess instanceof Map<?, ?> clientAccessMap)) {
            return Set.of();
        }

        return extractRoles(clientAccessMap).stream()
                .flatMap(role -> Stream.of(
                        SecurityAuthorityPrefixes.ROLE_PREFIX + normalize(role),
                        SecurityAuthorityPrefixes.CLIENT_ROLE_PREFIX + normalize(role)
                ))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Collection<GrantedAuthority> extractAuthorizationPermissions(Jwt jwt) {
        Map<String, Object> authorization = jwt.getClaimAsMap(JwtClaimNames.AUTHORIZATION);
        if (authorization == null) {
            return Set.of();
        }

        Object permissionsObject = authorization.get(JwtClaimNames.PERMISSIONS);
        if (!(permissionsObject instanceof List<?> permissions)) {
            return Set.of();
        }

        Set<String> values = new HashSet<>();

        for (Object permission : permissions) {
            if (!(permission instanceof Map<?, ?> permissionMap)) {
                continue;
            }

            Object resourceName = permissionMap.get("rsname");
            Object scopes = permissionMap.get("scopes");

            if (resourceName instanceof String resource && scopes instanceof List<?> scopeList) {
                for (Object scope : scopeList) {
                    if (scope instanceof String scopeValue && !scopeValue.isBlank()) {
                        values.add(SecurityAuthorityPrefixes.PERMISSION_PREFIX + normalize(resource) + ":" + normalize(scopeValue));
                    }
                }
            }
        }

        return values.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> extractRoles(Map<?, ?> accessMap) {
        Object roles = accessMap.get(JwtClaimNames.ROLES);
        if (!(roles instanceof Collection<?> roleCollection)) {
            return Set.of();
        }

        return roleCollection.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String resolvePrincipalName(Jwt jwt) {
        String claimName = securityProperties.principalClaim();
        String claimValue = jwt.getClaimAsString(claimName);

        if (claimValue != null && !claimValue.isBlank()) {
            return claimValue;
        }

        String preferredUsername = jwt.getClaimAsString(JwtClaimNames.PREFERRED_USERNAME);
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }

        return jwt.getSubject();
    }

    /**
     * Permite nombrar los roles en Keycloak como es costumbre allí —minúsculas y guiones— y
     * comprobarlos aquí como es costumbre en Spring: {@code config-read} se convierte en
     * {@code CONFIG_READ}, listo para {@code hasRole("CONFIG_READ")}.
     *
     * <p>La conversión no es inyectiva: {@code config-read}, {@code config_read} y
     * {@code Config Read} producen la misma autoridad. Ya no es un problema de seguridad —los roles
     * de realm y los de cliente viven en prefijos distintos—, pero sigue siendo motivo para no
     * crear en el mismo cliente dos roles que solo se diferencien en el separador.</p>
     */
    private String normalize(String value) {
        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase();
    }
}
