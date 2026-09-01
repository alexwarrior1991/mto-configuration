package com.alejandro.mtoconfiguration.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.metadata.MethodType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Ningun controlador puede añadir restricciones de Bean Validation a un metodo que sobrescribe.
 *
 * <p>La regla es de la especificacion (seccion 5.6.5, {@code HV000151}): un metodo que sobrescribe
 * a otro no puede redefinir las restricciones de sus parametros. Saltarsela no da un error de
 * compilacion ni un fallo al arrancar: revienta <b>en tiempo de peticion</b>, la primera vez que
 * Spring valida los argumentos de ese controlador, y sale por HTTP como un 500 generico.</p>
 *
 * <p>Y es peor de lo que parece, porque el fallo no es del metodo culpable sino de la clase: los
 * metadatos de validacion se construyen para el controlador entero, asi que un unico
 * {@code @Valid} de mas tumba TODOS los endpoints de esa clase que pasen por validacion de metodo
 * —tipicamente los {@code /bulk}, por su {@code List<@Valid DTO>}—. Asi aparecio: los
 * {@code POST /bulk} y {@code PUT /bulk} de los ocho controladores de infraestructura devolvian
 * 500 porque {@code create}, {@code bulkCreate} y {@code search} redeclaraban con {@code @Valid}
 * firmas que ya existian en {@code SaveController} y {@code SearchController}.</p>
 *
 * <p>Pedir el descriptor de la clase es exactamente lo que hace Spring antes de validar, asi que
 * este test reproduce el fallo sin levantar el contexto web ni una peticion por endpoint.</p>
 */
class ControllerConstraintDeclarationTest {

    private static final String CONTROLLER_PACKAGE = "com.alejandro.mtoconfiguration.controller";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("controllers")
    @DisplayName("los metadatos de validacion del controlador se construyen sin conflicto")
    void metadatosDeValidacionCoherentes(Class<?> controller) {
        assertThatCode(() -> validator.getConstraintsForClass(controller).getConstrainedMethods(MethodType.NON_GETTER, MethodType.GETTER))
                .doesNotThrowAnyException();
    }

    static List<Class<?>> controllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> found = scanner.findCandidateComponents(CONTROLLER_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(ControllerConstraintDeclarationTest::loadClass)
                .sorted(java.util.Comparator.comparing(Class::getName))
                .collect(Collectors.toList());

        if (found.isEmpty()) {
            throw new IllegalStateException("El escaneo no encontro ningun controlador: revisar " + CONTROLLER_PACKAGE);
        }

        return found;
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se pudo cargar " + name, e);
        }
    }
}
