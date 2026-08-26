package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.configuration.AsyncConfiguration;
import com.alejandro.mtoconfiguration.core.audit.SpringSecurityAuditorAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code SecurityContextHolder} guarda el contexto en un {@code ThreadLocal}, así que la identidad
 * del usuario no cruza al hilo virtual por sí sola. La pérdida es silenciosa —no hay excepción, solo
 * un contexto vacío—, de modo que sin estas pruebas la regresión solo se vería mucho después, en las
 * columnas de auditoría de la base de datos.
 */
class AsyncSecurityContextPropagationTest {

    private static final String USERNAME = "ana.perez";

    private final AsyncTaskExecutor executor = new AsyncConfiguration().applicationTaskExecutor();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("el executor de @Async lleva la autenticación al hilo de trabajo")
    void elExecutorLlevaLaAutenticacionAlHiloDeTrabajo() throws Exception {
        autenticar();

        Future<String> usuarioEnElHilo = executor.submit(nombreDelUsuarioAutenticado());

        assertThat(usuarioEnElHilo.get(5, TimeUnit.SECONDS)).isEqualTo(USERNAME);
    }

    @Test
    @DisplayName("la tarea corre de verdad en otro hilo, así que la propagación no es un espejismo")
    void laTareaCorreEnOtroHilo() throws Exception {
        autenticar();

        Future<String> hiloDeTrabajo = executor.submit(() -> Thread.currentThread().getName());

        assertThat(hiloDeTrabajo.get(5, TimeUnit.SECONDS)).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("sin el envoltorio de seguridad el contexto se pierde: es la regresión que se vigila")
    void sinEnvoltorioElContextoSePierde() throws Exception {
        autenticar();

        AsyncTaskExecutor sinEnvoltorio = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        Future<String> usuarioEnElHilo = sinEnvoltorio.submit(nombreDelUsuarioAutenticado());

        assertThat(usuarioEnElHilo.get(5, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("la auditoría atribuye la escritura asíncrona al usuario real, no a «system»")
    void laAuditoriaAtribuyeLaEscrituraAsincronaAlUsuarioReal() throws Exception {
        autenticar();
        SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware(new CurrentUserService());

        Future<Optional<String>> auditor = executor.submit(auditorAware::getCurrentAuditor);

        assertThat(auditor.get(5, TimeUnit.SECONDS)).contains(USERNAME);
    }

    private Callable<String> nombreDelUsuarioAutenticado() {
        return () -> {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            return autenticacion == null ? null : autenticacion.getName();
        };
    }

    private void autenticar() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("6f1b1c8e-0000-4000-8000-000000000001")
                .claim(JwtClaimNames.PREFERRED_USERNAME, USERNAME)
                .build();

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new JwtAuthenticationToken(
                jwt, AuthorityUtils.createAuthorityList("ROLE_" + SecurityRoles.CONFIG_WRITE), USERNAME));
        SecurityContextHolder.setContext(contexto);
    }
}
