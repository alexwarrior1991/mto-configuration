package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.CantileverValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.context.annotation.SessionScope;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorBeanContractTest {

    private static final String VALIDATOR_PACKAGE = "com.alejandro.mtoconfiguration.validator";

    /**
     * Un validador con ámbito de petición revienta en cuanto se le llama fuera de una: es
     * exactamente lo que hacen los métodos {@code @Async} de {@code BaseAsyncService}, que ejecutan
     * en un hilo del pool sin {@code RequestAttributes} ligadas.
     */
    @Test
    @DisplayName("ningún validador está ligado al ámbito de la petición")
    void ningunValidadorEsRequestScope() {
        Set<Class<?>> scoped = validatorClasses().stream()
                .filter(type -> type.isAnnotationPresent(RequestScope.class)
                        || type.isAnnotationPresent(SessionScope.class)
                        || type.isAnnotationPresent(Scope.class))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(scoped)
                .withFailMessage("Estos validadores llevan ámbito propio y fallarán desde un hilo @Async: %s", scoped)
                .isEmpty();
    }

    /**
     * Los validadores son singletons compartidos: si guardasen las alertas en un campo, dos
     * peticiones concurrentes se pisarían y la segunda vería los errores de la primera.
     */
    @Test
    @DisplayName("validar dos veces el mismo DTO da el mismo resultado")
    void losValidadoresNoAcumulanEstado() {
        CantileverValidator validator = new CantileverValidator(new SteadyArmValidator());

        List<Alert> primera = validator.validateBeforeSave(ValidDtos.rootCantilever());
        List<Alert> segunda = validator.validateBeforeSave(ValidDtos.rootCantilever());

        assertThat(primera).isEmpty();
        assertThat(segunda).isEmpty();
    }

    @Test
    @DisplayName("un DTO inválido no contamina la validación siguiente")
    void unaValidacionFallidaNoContaminaLaSiguiente() {
        CantileverValidator validator = new CantileverValidator(new SteadyArmValidator());

        assertThat(validator.validateBeforeSave(null)).hasSize(1);
        assertThat(validator.validateBeforeSave(ValidDtos.rootCantilever())).isEmpty();
    }

    @Test
    @DisplayName("todos los validadores concretos son componentes de Spring")
    void todosLosValidadoresSonComponentes() {
        assertThat(validatorClasses()).isNotEmpty();
    }

    private static Set<Class<?>> validatorClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CRUDValidator.class));

        return scanner.findCandidateComponents(VALIDATOR_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(ValidatorBeanContractTest::loadClass)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No se pudo cargar " + name, e);
        }
    }
}
