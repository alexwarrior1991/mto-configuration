package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.controller.commons.CRUDController;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El historial de una fila ({@code GET /{recurso}/{id}/revisions}) lo sirven dos metodos, uno en
 * {@link CRUDController} y otro en {@link AbstractLovController}, y lo hereda cada maestro y cada
 * catalogo con su ruta. Su permiso ({@code config-audit} ademas de {@code config-read}) no es una
 * anotacion de esos metodos sino una regla de {@code SecurityConfiguration} sobre
 * {@link ConfigurationApiPaths#REVISIONS_PATTERN}, y {@code PathPattern} no deja escribirla con
 * {@code /**} en medio: cubre un recurso de <b>un</b> segmento.
 *
 * <p>Un controlador montado mas hondo ({@code /infra/tracks}) o fuera del prefijo de la API
 * serviria su historial solo con {@code config-read}, o con un token cualquiera, sin que nada se
 * rompiera. Esto lo ve: recorre los controladores reales y comprueba que la ruta de historial de
 * cada uno cae en el patron protegido. Que el patron pida los dos permisos lo prueba
 * {@code ApiAuthorizationRulesTest}.
 */
class RevisionsRouteGuardTest {

    private static final String PAQUETE = "com.alejandro.mtoconfiguration.controller";

    private static final PathPattern PROTEGIDO =
            PathPatternParser.defaultInstance.parse(ConfigurationApiPaths.REVISIONS_PATTERN);

    @Test
    @DisplayName("las dos clases base sirven el historial en la ruta de la constante")
    void lasClasesBaseSirvenElHistorial() {
        for (Class<?> base : Set.of(CRUDController.class, AbstractLovController.class)) {
            Method revisions = Arrays.stream(base.getDeclaredMethods())
                    .filter(method -> method.getName().equals("revisions"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(base.getSimpleName() + " no sirve el historial"));

            assertThat(revisions.getAnnotation(GetMapping.class).value())
                    .containsExactly(ConfigurationApiPaths.REVISIONS);
        }
    }

    @Test
    @DisplayName("la ruta de historial de cada maestro y de cada catalogo cae en el patron que pide config-audit")
    void cadaRutaDeHistorialEstaProtegida() {
        Set<Class<?>> controladores = controladoresConHistorial();

        assertThat(controladores)
                .withFailMessage("No se encontro ningun controlador con historial: el escaneo no mira donde debe")
                .hasSizeGreaterThan(20);

        for (Class<?> controlador : controladores) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controlador, RequestMapping.class);

            assertThat(mapping)
                    .withFailMessage("%s no declara su ruta en la clase", controlador.getSimpleName())
                    .isNotNull();

            for (String base : mapping.value()) {
                String ruta = base + "/1/revisions";

                assertThat(PROTEGIDO.matches(PathContainer.parsePath(ruta)))
                        .withFailMessage("%s sirve su historial en %s, fuera de %s: se leeria sin config-audit",
                                controlador.getSimpleName(), ruta, ConfigurationApiPaths.REVISIONS_PATTERN)
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("ningun controlador redefine el historial: lo heredaria con otra ruta o sin su paginacion")
    void nadieRedefineElHistorial() {
        for (Class<?> controlador : controladoresConHistorial()) {
            assertThat(Arrays.stream(controlador.getDeclaredMethods()).map(Method::getName))
                    .withFailMessage("%s redefine revisions", controlador.getSimpleName())
                    .doesNotContain("revisions");
        }
    }

    private Set<Class<?>> controladoresConHistorial() {
        ClassPathScanningCandidateComponentProvider escaner =
                new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AssignableTypeFilter(CRUDController.class));
        escaner.addIncludeFilter(new AssignableTypeFilter(AbstractLovController.class));

        return escaner.findCandidateComponents(PAQUETE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(this::cargar)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Class<?> cargar(String nombre) {
        try {
            return Class.forName(nombre);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(nombre, e);
        }
    }
}
