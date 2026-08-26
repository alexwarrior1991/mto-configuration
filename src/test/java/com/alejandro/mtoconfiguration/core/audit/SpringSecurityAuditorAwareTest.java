package com.alejandro.mtoconfiguration.core.audit;

import com.alejandro.mtoconfiguration.configuration.security.CurrentUserService;
import com.alejandro.mtoconfiguration.configuration.security.JwtClaimNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El valor que se escribe aquí sobrevive a la incidencia que lo provocó: acaba en las columnas de
 * auditoría y en las revisiones de Envers, y es lo que alguien leerá meses después para saber quién
 * cambió un dato maestro. Que «lo hizo un proceso» y «no se sabe quién fue» se registraran igual
 * convertía un fallo en un dato de aspecto legítimo.
 */
class SpringSecurityAuditorAwareTest {

    private final SpringSecurityAuditorAware auditorAware =
            new SpringSecurityAuditorAware(new CurrentUserService());

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("con usuario autenticado se audita su nombre")
    void conUsuarioAutenticadoSeAuditaSuNombre() {
        hayPeticionHttp();
        autenticar("ana.perez");

        assertThat(auditorAware.getCurrentAuditor()).contains("ana.perez");
    }

    @Test
    @DisplayName("un proceso de fondo se audita como system")
    void unProcesoDeFondoSeAuditaComoSystem() {
        // Sin atributos de petición: es el publicador del outbox, un listener o una tarea programada.
        assertThat(auditorAware.getCurrentAuditor()).contains(SpringSecurityAuditorAware.SYSTEM);
    }

    @Test
    @DisplayName("una petición HTTP sin autenticación se audita como unknown, no como system")
    void unaPeticionSinAutenticacionSeAuditaComoUnknown() {
        hayPeticionHttp();

        assertThat(auditorAware.getCurrentAuditor()).contains(SpringSecurityAuditorAware.UNKNOWN);
        assertThat(auditorAware.getCurrentAuditor()).isNotEqualTo(
                java.util.Optional.of(SpringSecurityAuditorAware.SYSTEM));
    }

    @Test
    @DisplayName("un usuario llamado literalmente «system» sigue auditándose por su nombre")
    void unUsuarioLlamadoSystemSeAuditaPorSuNombre() {
        hayPeticionHttp();
        autenticar(SpringSecurityAuditorAware.SYSTEM);

        // El texto coincide con el del proceso interno, pero aquí procede de un JWT: lo que separa
        // ambos casos en una investigación es el WARN que solo se emite en el otro.
        assertThat(auditorAware.getCurrentAuditor()).contains(SpringSecurityAuditorAware.SYSTEM);
    }

    private void hayPeticionHttp() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private void autenticar(String usuario) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("uuid")
                .claim(JwtClaimNames.PREFERRED_USERNAME, usuario)
                .build();

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new JwtAuthenticationToken(
                jwt, AuthorityUtils.NO_AUTHORITIES, usuario));
        SecurityContextHolder.setContext(contexto);
    }
}
