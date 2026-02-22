package com.alejandro.mtoconfiguration.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class PermissionUtils {

    private static <T> T getJwtTokenValue(Function<Jwt, T> extractor, T defaultValue) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && (authentication.getPrincipal() instanceof Jwt jwt)) {
            return extractor.apply(jwt);
        } else if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return extractor.apply(jwtToken.getToken());
        }
        return defaultValue;
    }


    /**
     * Retrieves the JWT token from the current security context.
     *
     * @return An Optional containing the JWT token if present, empty otherwise
     */
    public static Optional<Jwt> getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return Optional.of(jwtToken.getToken());
        }
        return Optional.empty();
    }

    /**
     * Checks if the current user is authenticated.
     *
     * @return true if the user is authenticated and not anonymous, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String principal && principal.equals("anonymousUser"));
    }

    /**
     * Retrieves all realm-level roles assigned to the current user.
     *
     * @return A Set of realm role names, empty set if none found
     */
    public static Set<String> getRealmRoles() {
        return getJwt()
                .map(jwt -> jwt.getClaimAsMap("realm_access"))
                .map(realmAccess -> realmAccess.get("roles"))
                .flatMap(v -> (v instanceof List<?> list) ? Optional.of(list) : Optional.empty())
                .map(PermissionUtils::toStringSet)
                .orElseGet(Collections::emptySet);
    }

    /**
     * Obtiene todos los roles del Realm de Keycloak usando el extractor genérico.
     */
    public static Set<String> getCurrentRoles() {
        return getJwtTokenValue(
                jwt -> {
                    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                    if (realmAccess != null && realmAccess.containsKey("roles")) {
                        Object rolesObj = realmAccess.get("roles");
                        if (rolesObj instanceof Collection<?>) {
                            return ((Collection<?>) rolesObj).stream()
                                    .filter(role -> role instanceof String)
                                    .map(role -> (String) role)
                                    .collect(Collectors.toSet());
                        }
                    }
                    return Set.of();
                },
                Set.of()
        );
    }


    /**
     * Retrieves all client-specific roles for the given client ID.
     *
     * @param clientId The client identifier to retrieve roles for
     * @return A Set of client role names, empty set if none found
     */
    public static Set<String> getClientRoles(String clientId) {
        return getJwt()
                .map(jwt -> jwt.getClaimAsMap("resource_access"))
                .map(resourceAccess -> resourceAccess.get(clientId))
                .flatMap(v -> (v instanceof Map<?, ?> m) ? Optional.of(m) : Optional.empty())
                .map(clientAccess -> clientAccess.get("roles"))
                .flatMap(v -> (v instanceof List<?> list) ? Optional.of(list) : Optional.empty())
                .map(PermissionUtils::toStringSet)
                .orElseGet(Collections::emptySet);
    }

    /**
     * Checks if the current user has the specified realm role.
     *
     * @param role The role name to check
     * @return true if the user has the role, false otherwise
     */
    public static boolean hasRealmRole(String role) {
        return getRealmRoles().contains(role);
    }

    /**
     * Checks if the current user has any of the specified realm roles.
     *
     * @param roles The role names to check
     * @return true if the user has at least one of the roles, false otherwise
     */
    public static boolean hasAnyRealmRole(String... roles) {
        Set<String> userRoles = getRealmRoles();
        return Arrays.stream(roles).anyMatch(userRoles::contains);
    }

    /**
     * Retrieves the unique identifier (subject) of the current user.
     *
     * @return The user ID, or null if not available
     */
    public static String getUserId() {
        return getJwt().map(Jwt::getSubject).orElse(null);
    }

    /**
     * Retrieves the preferred username of the current user.
     *
     * @return The username, or "anonymous" if not available
     */
    public static String getUsername() {
        return getJwt().map(jwt -> jwt.getClaimAsString("preferred_username")).orElse("anonymous");
    }

    /**
     * Retrieves the email address of the current user.
     *
     * @return The email address, or null if not available
     */
    public static String getEmail() {
        return getJwt().map(jwt -> jwt.getClaimAsString("email")).orElse(null);
    }

    /**
     * Retrieves all custom attributes (claims) from the JWT token.
     *
     * @return A Map containing all claims, empty map if not available
     */
    public static Map<String, Object> getCustomAttributes() {
        return getJwt().map(Jwt::getClaims).orElse(Collections.emptyMap());
    }

    /**
     * Retrieves the name of the Realm to which the token belongs.
     * (Assumes the issuer URL ends with /realms/{realmName})
     *
     * @return The realm name, or null if not available
     */
    public static String getRealmName() {
        return getJwt().map(jwt -> {
            String issuer = jwt.getIssuer().toString();
            return issuer.substring(issuer.lastIndexOf("/") + 1);
        }).orElse(null);
    }

    /**
     * Retrieves all granted authorities for the current user.
     *
     * @return A List of authority names, empty list if none found
     */
    public static List<String> getAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Collections.emptyList();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    /**
     * Checks if the current user has the specified authority.
     *
     * @param authority The authority name to check
     * @return true if the user has the authority, false otherwise
     */
    public static boolean hasAuthority(String authority) {
        return getAuthorities().contains(authority);
    }

    /**
     * Retrieves all OAuth2 scopes granted to the current user.
     *
     * @return A List of scope names, empty list if none found
     */
    public static List<String> getScopes() {
        return getJwt()
                .map(jwt -> jwt.getClaimAsStringList("scope"))
                .orElse(Collections.emptyList());
    }

    /**
     * Checks if the current user has the specified OAuth2 scope.
     *
     * @param scope The scope name to check
     * @return true if the user has the scope, false otherwise
     */
    public static boolean hasScope(String scope) {
        return getScopes().contains(scope);
    }


    /**
     * Converts a list of objects to a set of non-blank strings.
     *
     * @param roles The list to convert
     * @return A Set containing only non-blank string values
     */
    private static Set<String> toStringSet(List<?> roles) {
        Set<String> out = new HashSet<>();
        for (Object role : roles) {
            if (role instanceof String s && !s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }
}
