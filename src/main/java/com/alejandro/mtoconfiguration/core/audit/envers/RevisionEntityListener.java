package com.alejandro.mtoconfiguration.core.audit.envers;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.envers.EntityTrackingRevisionListener;
import org.hibernate.envers.RevisionType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public class RevisionEntityListener implements EntityTrackingRevisionListener {

    private static final String SYSTEM_USER = "system";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String USER_AGENT_HEADER = "User-Agent";

    @Override
    public void entityChanged(Class entityClass, String entityName, Object entityId, RevisionType revisionType, Object revisionEntity) {
        final ExtendedRevisionEntity r = (ExtendedRevisionEntity) revisionEntity;
        r.addAuditModifiedEntity(entityName, convertEntityIdToLong(entityId));
    }

    @Override
    public void newRevision(Object revisionEntity) {

        ExtendedRevisionEntity revision = (ExtendedRevisionEntity) revisionEntity;

        revision.setUsername(getCurrentUserName());
        revision.setUserId(getCurrentUserId());
        revision.setIpAddress(getCurrentIpAddress());
        revision.setUserAgent(getCurrentUserAgent());
        revision.setRequestMethod(getCurrentRequestMethod());
        revision.setRequestUri(getCurrentRequestUri());
        revision.setCorrelationId(getCurrentCorrelationId());
    }

    private String getCurrentUserName() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(principal -> {
                    if (principal instanceof Jwt jwt) {
                        String preferredUsername = jwt.getClaimAsString("preferred_username");
                        if (preferredUsername != null && !preferredUsername.isBlank()) {
                            return preferredUsername;
                        }

                        String username = jwt.getClaimAsString("username");
                        if (username != null && !username.isBlank()) {
                            return username;
                        }

                        String email = jwt.getClaimAsString("email");
                        if (email != null && !email.isBlank()) {
                            return email;
                        }

                        return jwt.getSubject();
                    }
                    return principal.toString();
                })
                .filter(username -> username != null && !username.isBlank())
                .orElse(SYSTEM_USER);
    }

    private String getCurrentUserId(){
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(principal -> {
                    if (principal instanceof Jwt jwt) {
                        return jwt.getSubject();
                    }
                    return null;
                })
                .orElse(null);
    }

    private String getCurrentIpAddress() {
        return Optional.ofNullable(getCurrentRequest())
                .map(request -> {
                    String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
                    if (forwardedFor != null && !forwardedFor.isBlank()) {
                        return forwardedFor.split(",")[0].trim();
                    }
                    return request.getRemoteAddr();
                })
                .orElse(null);
    }


    private String getCurrentUserAgent() {
        return Optional.ofNullable(getCurrentRequest())
                .map(request -> request.getHeader(USER_AGENT_HEADER))
                .orElse(null);
    }

    private String getCurrentRequestMethod() {
        return Optional.ofNullable(getCurrentRequest())
                .map(HttpServletRequest::getMethod)
                .orElse(null);
    }

    private String getCurrentRequestUri() {
        return Optional.ofNullable(getCurrentRequest())
                .map(HttpServletRequest::getRequestURI)
                .orElse(null);
    }

    private String getCurrentCorrelationId() {
        return Optional.ofNullable(getCurrentRequest())
                .map(request -> request.getHeader(CORRELATION_ID_HEADER))
                .filter(correlationId -> !correlationId.isBlank())
                .orElse(null);
    }

    private HttpServletRequest getCurrentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long convertEntityIdToLong(Object entityId) {
        switch (entityId) {
            case null -> {
                return null;
            }
            case Long longId -> {
                return longId;
            }
            case Number numberId -> {
                return numberId.longValue();
            }
            default -> {
            }
        }

        try {
            return Long.valueOf(entityId.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
