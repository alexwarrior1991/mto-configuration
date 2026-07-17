package com.alejandro.mtoconfiguration.core.audit;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {

    private final CurrentUserService currentUserService;

    public SpringSecurityAuditorAware(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(currentUserService.getUsername());
    }
}
