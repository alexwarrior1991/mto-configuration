package com.alejandro.mtoconfiguration.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * Nombre del executor que usan los {@code @Async} de la aplicación. Coincide a propósito con el
     * de Boot: al declararlo aquí, su autoconfiguración se retira ({@code @ConditionalOnMissingBean}
     * sobre {@code Executor}) y queda un único executor, el mismo que usa el dispatch asíncrono de
     * MVC. Un segundo bean con otro nombre dejaría dos, y las peticiones que devuelven
     * {@code CompletableFuture} podrían acabar en el que no propaga el contexto.
     */
    public static final String TASK_EXECUTOR = "applicationTaskExecutor";

    /**
     * {@code SecurityContextHolder} guarda el contexto en un {@code ThreadLocal}, así que un hilo
     * virtual recién creado arranca sin autenticación. Sin el envoltorio, todo lo que cuelga de la
     * identidad del usuario se rompe en la rama asíncrona y además lo hace en silencio: la
     * auditoría de {@code SpringSecurityAuditorAware} atribuiría cada escritura de {@code /async}
     * al usuario «system», y cualquier {@code @PreAuthorize} sobre un servicio fallaría por falta
     * de autenticación en vez de por falta de permisos.
     */
    @Bean(TASK_EXECUTOR)
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new DelegatingSecurityContextAsyncTaskExecutor(
                new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor()));
    }

}
