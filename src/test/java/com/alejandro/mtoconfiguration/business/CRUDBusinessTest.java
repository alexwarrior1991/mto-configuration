package com.alejandro.mtoconfiguration.business;

import com.alejandro.mtoconfiguration.business.commons.BaseBusiness;
import com.alejandro.mtoconfiguration.business.commons.Business;
import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Contrato de la capa de negocio.
 *
 * <p>Los ocho {@code Business} concretos estan hoy vacios: heredan todo de {@link CRUDBusiness} y
 * existen como puntos de extension. Eso es justamente lo que hay que proteger, porque
 * {@code BaseService} invoca sus hooks <b>incondicionalmente</b> en cada alta, modificacion,
 * cancelacion y borrado. Un hook que dejara de tolerar un nulo, o un {@code deleteEntity} que
 * dejara de marcar la entidad, romperia las ocho entidades a la vez y sin sintoma local.</p>
 */
class CRUDBusinessTest {

    private static final String BUSINESS_PACKAGE = "com.alejandro.mtoconfiguration.business";

    private static class TestDTO extends BaseDTO {
    }

    private static class TestEntity extends CRUDEntity {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    @Nested
    @DisplayName("Borrado")
    class Borrado {

        @Test
        @DisplayName("deleteEntity marca la entidad como borrada")
        void marcaComoBorrada() {
            // Es lo unico que CRUDService delega en el business durante el borrado logico: si
            // dejara de marcarla, la fila seguiria apareciendo en todas las consultas.
            CRUDBusiness<TestDTO, TestEntity> business = new CRUDBusiness<>();
            TestEntity entity = new TestEntity();

            assertThat(entity.isDeleted()).isFalse();

            business.deleteEntity(entity);

            assertThat(entity.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("marcar dos veces es idempotente")
        void idempotente() {
            CRUDBusiness<TestDTO, TestEntity> business = new CRUDBusiness<>();
            TestEntity entity = new TestEntity();

            business.deleteEntity(entity);
            business.deleteEntity(entity);

            assertThat(entity.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Hooks")
    class Hooks {

        @Test
        @DisplayName("los hooks por defecto no hacen nada y toleran nulos")
        void hooksToleranNulos() {
            // BaseService los llama sin comprobar nada; el dia que uno deje de tolerar un nulo, el
            // fallo aparece dentro de una transaccion y lejos de aqui.
            BaseBusiness<TestDTO, TestEntity> business = new BaseBusiness<>();
            TestDTO dto = new TestDTO();
            TestEntity entity = new TestEntity();

            assertThatCode(() -> {
                business.preMapperDTOToEntity(null, null);
                business.preMapperEntityToDTO(null, null);
                business.postMapperDTOToEntity(null, null);
                business.postMapperEntityToDTO(null, null);
                business.preCancelDTOToEntity(null, null);
                business.preCancelEntityToDTO(null, null);
                business.postCancelDTOToEntity(null, null);
                business.postCancelEntityToDTO(null, null);
                business.preDeleteDTOToEntity(null, null);
                business.preDeleteEntityToDTO(null, null);
                business.postDeleteDTOToEntity(null, null);
                business.postDeleteEntityToDTO(null, null);
                business.postValidationDTOToEntity(null, null);
            }).doesNotThrowAnyException();

            assertThatCode(() -> {
                business.preMapperDTOToEntity(dto, entity);
                business.postValidationDTOToEntity(dto, entity);
                business.preDeleteDTOToEntity(dto, entity);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("los hooks por defecto no modifican ni el DTO ni la entidad")
        void hooksNoModifican() {
            BaseBusiness<TestDTO, TestEntity> business = new BaseBusiness<>();
            TestDTO dto = new TestDTO();
            dto.setId(1L);
            TestEntity entity = new TestEntity();
            entity.setId(2L);

            business.preMapperDTOToEntity(dto, entity);
            business.postValidationDTOToEntity(dto, entity);
            business.preMapperEntityToDTO(entity, dto);
            business.postMapperEntityToDTO(entity, dto);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(entity.getId()).isEqualTo(2L);
            assertThat(entity.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("la interfaz declara exactamente los hooks que BaseService invoca")
        void superficieDelContrato() {
            // Si alguien añade un hook a la interfaz sin llamarlo desde el servicio, o al reves,
            // este test obliga a decidirlo a proposito.
            List<String> hooks = Arrays.stream(Business.class.getDeclaredMethods())
                    .map(Method::getName)
                    .sorted()
                    .toList();

            assertThat(hooks).containsExactly(
                    "postCancelDTOToEntity", "postCancelEntityToDTO",
                    "postDeleteDTOToEntity", "postDeleteEntityToDTO",
                    "postMapperDTOToEntity", "postMapperEntityToDTO",
                    "postValidationDTOToEntity",
                    "preCancelDTOToEntity", "preCancelEntityToDTO",
                    "preDeleteDTOToEntity", "preDeleteEntityToDTO",
                    "preMapperDTOToEntity", "preMapperEntityToDTO");
        }
    }

    @Nested
    @DisplayName("Los ocho business concretos")
    class BusinessConcretos {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.alejandro.mtoconfiguration.business.CRUDBusinessTest#businessClasses")
        @DisplayName("es componente de Spring, extiende CRUDBusiness y es serializable")
        void contratoDeCadaBusiness(Class<?> businessClass) {
            // Serializable no es decorativo: BaseService guarda el business como colaborador y la
            // interfaz Business lo exige.
            assertThat(businessClass).hasAnnotation(Component.class);
            assertThat(CRUDBusiness.class).isAssignableFrom(businessClass);
            assertThat(Serializable.class).isAssignableFrom(businessClass);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.alejandro.mtoconfiguration.business.CRUDBusinessTest#businessClasses")
        @DisplayName("se puede instanciar sin colaboradores y marca la entidad al borrar")
        void borradoDeCadaBusiness(Class<?> businessClass) throws Exception {
            @SuppressWarnings("unchecked")
            CRUDBusiness<BaseDTO, CRUDEntity> business =
                    (CRUDBusiness<BaseDTO, CRUDEntity>) businessClass.getDeclaredConstructor().newInstance();

            TestEntity entity = new TestEntity();
            business.deleteEntity(entity);

            assertThat(entity.isDeleted()).isTrue();
        }
    }

    static List<Class<?>> businessClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(CRUDBusiness.class));

        List<Class<?>> found = scanner.findCandidateComponents(BUSINESS_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(CRUDBusinessTest::loadClass)
                .filter(clazz -> !clazz.equals(CRUDBusiness.class))
                .sorted(java.util.Comparator.comparing(Class::getName))
                .collect(Collectors.toList());

        if (found.isEmpty()) {
            throw new IllegalStateException("El escaneo no encontro ningun business: revisar " + BUSINESS_PACKAGE);
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
