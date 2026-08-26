package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El {@code @PreAuthorize} de las listas de valores vive una sola vez, en
 * {@link AbstractLovController}, y llega a los veintitantos controladores concretos por herencia.
 * Eso se sostiene únicamente mientras ninguno redefina esos métodos: un {@code @Override} sin volver
 * a anotar dejaría ese recurso sin la comprobación y no rompería nada visible.
 */
class LovControllerGuardInheritanceTest {

    private static final String PAQUETE = "com.alejandro.mtoconfiguration.controller.synchronous.lov";

    private static final List<String> METODOS_PROTEGIDOS =
            List.of("create", "bulkCreate", "update", "bulkUpdate", "delete");

    @Test
    @DisplayName("los métodos de escritura de la clase base llevan la comprobación de lov-manage")
    void laClaseBaseLlevaLaComprobacion() {
        for (Method metodo : AbstractLovController.class.getDeclaredMethods()) {
            if (METODOS_PROTEGIDOS.contains(metodo.getName())) {
                assertThat(metodo.getAnnotation(PreAuthorize.class))
                        .withFailMessage("%s debería exigir %s", metodo.getName(), SecurityRoles.LOV_MANAGE)
                        .isNotNull();
                assertThat(metodo.getAnnotation(PreAuthorize.class).value())
                        .contains(SecurityRoles.LOV_MANAGE);
            }
        }
    }

    @Test
    @DisplayName("ningún controlador de LOV redefine un método protegido sin volver a anotarlo")
    void ningunControladorPierdeLaComprobacionHeredada() {
        Set<Class<?>> controladores = controladoresDeLov();

        assertThat(controladores)
                .withFailMessage("No se encontró ningún controlador de LOV: el escaneo no está mirando donde debe")
                .isNotEmpty();

        for (Class<?> controlador : controladores) {
            for (Method metodo : controlador.getDeclaredMethods()) {
                if (METODOS_PROTEGIDOS.contains(metodo.getName())) {
                    assertThat(metodo.getAnnotation(PreAuthorize.class))
                            .withFailMessage(
                                    "%s redefine %s y pierde la comprobación heredada de %s",
                                    controlador.getSimpleName(), metodo.getName(), SecurityRoles.LOV_MANAGE)
                            .isNotNull();
                }
            }
        }
    }

    private Set<Class<?>> controladoresDeLov() {
        ClassPathScanningCandidateComponentProvider escaner =
                new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AssignableTypeFilter(AbstractLovController.class));

        return escaner.findCandidateComponents(PAQUETE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(this::cargar)
                .filter(clase -> !clase.equals(AbstractLovController.class))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Class<?> cargar(String nombre) {
        try {
            return Class.forName(nombre);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(nombre, e);
        }
    }
}
