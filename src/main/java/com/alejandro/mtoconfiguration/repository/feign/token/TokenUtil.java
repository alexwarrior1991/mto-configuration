package com.alejandro.mtoconfiguration.repository.feign.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class to handle operations related to the JSON Web Token (JWT) retrieved
 * from the Spring Security context. This class provides methods to extract specific claims
 * and other relevant information from the current JWT token.
 * <p>
 * This class is typically used to interact with tokens stored in the {@link SecurityContextHolder}
 * and retrieve data such as claims, permissions, and other properties encapsulated in the {@link Jwt} object.
 */
@Slf4j
@Service
public class TokenUtil {


    public Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        return null;
    }

    public String getPreferredUsername() {
        return Optional.ofNullable(getJwt())
                .map(jwt -> jwt.getClaimAsString("preferred_username"))
                .orElse("anonymous");
    }

    public String getName() {
        return Optional.ofNullable(getJwt())
                .map(jwt -> jwt.getClaimAsString("name"))
                .orElse(null);
    }

    public String getEmail() {
        return Optional.ofNullable(getJwt())
                .map(jwt -> jwt.getClaimAsString("email"))
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPermissions() {
        Jwt jwt = getJwt();
        if (jwt == null) return Collections.emptyList();

        Map<String, Object> authorization = jwt.getClaim("authorization");
        if (authorization != null && authorization.containsKey("permissions")) {
            return (List<Map<String, Object>>) authorization.get("permissions");
        }
        return Collections.emptyList();
    }

    public Object getClaim(String claimName) {
        return Optional.ofNullable(getJwt())
                .map(jwt -> jwt.getClaim(claimName))
                .orElse(null);
    }


}
