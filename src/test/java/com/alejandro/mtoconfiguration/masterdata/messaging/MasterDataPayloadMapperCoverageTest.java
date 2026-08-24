package com.alejandro.mtoconfiguration.masterdata.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigila que toda entidad publicada como dato maestro tenga su mapper explicito.
 * <p>
 * SteadyArm estaba anotada con {@code @PublishMasterDataEvent} y era la unica sin
 * mapper, asi que caia en la serializacion generica de la entidad JPA. Como
 * SteadyArm.cantilever <-> Cantilever.steadyArm es bidireccional y no hay ningun
 * {@code @JsonIgnore} en el paquete de entidades, Jackson entraba en recursion
 * infinita dentro de la transaccion de negocio: cualquier alta, modificacion o baja
 * de un SteadyArm reventaba. Sin este test, la siguiente entidad anotada vuelve a
 * caer en la misma trampa sin que salte nada hasta produccion.
 */
class MasterDataPayloadMapperCoverageTest {

    private static final String ENTITY_PACKAGE = "com.alejandro.mtoconfiguration.entity";
    private static final String MAPPER_PACKAGE = "com.alejandro.mtoconfiguration.masterdata.messaging.mapper";

    @Test
    void todaEntidadPublicadaTieneSuMapper() {
        Set<String> publicadas = entidadesPublicadas().stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> conMapper = mappers().keySet().stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(publicadas)
                .as("hay entidades anotadas sin mapper; sin el, publicarlas provoca "
                        + "recursion infinita en sus relaciones bidireccionales")
                .isNotEmpty()
                .allMatch(conMapper::contains);
    }

    @Test
    void ningunMapperDevuelveUnPayloadVacio() throws Exception {
        // CantileverMasterDataPayloadMapper devolvia Map.of(): el evento viajaba sin
        // ningun dato y los metodos de mapeo eran codigo muerto.
        for (Map.Entry<Class<?>, MasterDataEntityPayloadMapper<Object>> entry : mappers().entrySet()) {
            Object entidad = entry.getKey().getDeclaredConstructor().newInstance();

            assertThat(entry.getValue().toPayload(entidad))
                    .as("%s produce un payload vacio", entry.getValue().getClass().getSimpleName())
                    .isNotEmpty();
        }
    }

    @Test
    void ningunMapperSeSolapaConOtro() {
        Set<Class<?>> tipos = mappers().keySet();

        assertThat(tipos)
                .as("dos mappers para el mismo tipo hacen que el elegido dependa del orden de escaneo")
                .doesNotHaveDuplicates();
    }

    private Set<Class<?>> entidadesPublicadas() {
        return scan(ENTITY_PACKAGE, new AnnotationTypeFilter(PublishMasterDataEvent.class));
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, MasterDataEntityPayloadMapper<Object>> mappers() {
        Map<Class<?>, MasterDataEntityPayloadMapper<Object>> byType = new LinkedHashMap<>();

        for (Class<?> type : scan(MAPPER_PACKAGE, new AssignableTypeFilter(MasterDataEntityPayloadMapper.class))) {
            try {
                MasterDataEntityPayloadMapper<Object> mapper =
                        (MasterDataEntityPayloadMapper<Object>) type.getDeclaredConstructor().newInstance();
                byType.put(mapper.supportedType(), mapper);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("No se ha podido instanciar " + type.getName(), exception);
            }
        }

        return byType;
    }

    private Set<Class<?>> scan(String basePackage, org.springframework.core.type.filter.TypeFilter filter) {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(filter);

        return provider.findCandidateComponents(basePackage).stream()
                .map(BeanDefinition::getBeanClassName)
                .map(name -> {
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
