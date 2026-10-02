package com.alejandro.mtoconfiguration.mapper.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.mapper.lov.AnchorageFoundationMapperImpl;
import com.alejandro.mtoconfiguration.mapper.lov.FoundationMapperImpl;
import com.alejandro.mtoconfiguration.mapper.lov.PortalMapperImpl;
import com.alejandro.mtoconfiguration.mapper.lov.ProfileStatusMapperImpl;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.ProfileStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mapeo de listas de valores: la conversion generada y, sobre todo, {@code findOrMap}.
 *
 * <p>{@code findOrMap} es lo que decide si una LOV que llega dentro de otro DTO se <b>reutiliza</b>
 * de base de datos o se <b>crea</b> nueva. Equivocarse ahi no da error: inserta un duplicado en el
 * catalogo, que es precisamente lo que una lista de valores no debe permitir.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LovMapperTest {

    @Mock
    private ProfileStatusRepository repository;

    private final ProfileStatusMapperImpl mapper = new ProfileStatusMapperImpl();

    private static ProfileStatusDTO dto(Long id, String code) {
        ProfileStatusDTO dto = new ProfileStatusDTO();
        dto.setId(id);
        dto.setCode(code);
        dto.setDescription("Descripcion " + code);
        dto.setEnabled(true);
        return dto;
    }

    /**
     * Los mappers generados de los diecisiete catálogos, buscados en el classpath: uno nuevo entra
     * solo en los casos de «Todos los catálogos».
     */
    static List<Named<Class<?>>> lovMappers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(LovMapper.class));

        return scanner.findCandidateComponents("com.alejandro.mtoconfiguration.mapper.lov").stream()
                .map(BeanDefinition::getBeanClassName)
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, LovMapperTest.class.getClassLoader()))
                .sorted(Comparator.comparing(Class::getSimpleName))
                .map(type -> Named.<Class<?>>of(type.getSimpleName(), type))
                .toList();
    }

    private static ProfileStatus entity(Long id, String code) {
        ProfileStatus entity = new ProfileStatus();
        entity.setId(id);
        entity.setCode(code);
        entity.setDescription("Descripcion " + code);
        entity.setEnabled(true);
        return entity;
    }

    @Nested
    @DisplayName("Conversion")
    class Conversion {

        @Test
        @DisplayName("los campos de la LOV viajan en los dos sentidos")
        void idaYVuelta() {
            ProfileStatus entity = mapper.toEntity(dto(null, "OK"));

            assertThat(entity.getCode()).isEqualTo("OK");
            assertThat(entity.getDescription()).isEqualTo("Descripcion OK");
            assertThat(entity.isEnabled()).isTrue();

            ProfileStatusDTO dto = mapper.toDTO(entity(1L, "OK"));

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getCode()).isEqualTo("OK");
            assertThat(dto.getDescription()).isEqualTo("Descripcion OK");
            assertThat(dto.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("las propiedades de auditoria del DTO no se copian a la entidad")
        void auditoriaIgnorada() {
            ProfileStatusDTO dto = dto(null, "OK");
            dto.setCreateUser("intruso");
            dto.setCreateDate(LocalDateTime.of(2000, 1, 1, 0, 0));
            dto.setVersionNumber(99);

            ProfileStatus entity = mapper.toEntity(dto);

            assertThat(entity.getCreateUser()).isNull();
            assertThat(entity.getCreateDate()).isNull();
            assertThat(entity.getVersionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("la modificacion vuelca el DTO sobre una entidad existente sin tocar su auditoria")
        void actualizacion() {
            ProfileStatus entity = entity(1L, "OK");
            entity.setCreateUser("ana");
            entity.setVersionNumber(4);

            mapper.updateEntityFromDTO(dto(1L, "KO"), entity);

            assertThat(entity.getCode()).isEqualTo("KO");
            assertThat(entity.getCreateUser()).isEqualTo("ana");
            assertThat(entity.getVersionNumber()).isEqualTo(4);
        }

        @Test
        @DisplayName("un nulo se mapea a nulo en los dos sentidos")
        void nulos() {
            assertThat(mapper.toEntity(null)).isNull();
            assertThat(mapper.toDTO(null)).isNull();
        }
    }

    /**
     * Lo que fija {@code @ToEntityIgnoreAudit} en cada catálogo, no solo en {@code ProfileStatus}.
     *
     * <p>Un mapper que sobrescribe {@code toEntity} o {@code updateEntityFromDTO} para ignorar su tipo
     * padre pierde esa anotación si no la repite: MapStruct no hereda los {@code @Mapping}. Les pasó a
     * {@code foundations}, {@code portals} y {@code anchorage-foundations}, que copiaban del cuerpo el
     * id, la versión y la creación; como el JSON no lleva la creación, toda modificación acababa en un
     * 500, y un alta con id podía pisar esa fila.</p>
     */
    @Nested
    @DisplayName("Todos los catálogos")
    class TodosLosCatalogos {

        private static final LocalDateTime ANTES = LocalDateTime.of(2000, 1, 1, 0, 0);

        @SuppressWarnings("unchecked")
        private static LovMapper<LovDTO, Lov> instancia(Class<?> impl) throws ReflectiveOperationException {
            return (LovMapper<LovDTO, Lov>) impl.getDeclaredConstructor().newInstance();
        }

        /** {@code toEntity(XDTO)} del mapper generado, no el puente genérico. */
        private static Method toEntity(Class<?> impl) {
            return Arrays.stream(impl.getMethods())
                    .filter(method -> method.getName().equals("toEntity"))
                    .filter(method -> method.getParameterCount() == 1 && !method.isBridge())
                    .findFirst()
                    .orElseThrow();
        }

        private static <T> T nueva(Class<T> type) throws ReflectiveOperationException {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }

        /** Un cuerpo que trae id, versión y auditoría: nada de eso puede llegar a la entidad. */
        private static LovDTO cuerpo(Class<?> impl) throws ReflectiveOperationException {
            LovDTO dto = (LovDTO) nueva(toEntity(impl).getParameterTypes()[0]);
            dto.setId(99L);
            dto.setCode("NUEVO");
            dto.setDescription("Descripcion NUEVO");
            dto.setEnabled(true);
            dto.setVersionNumber(7);
            dto.setVersionDate(ANTES);
            dto.setVersionUser("intruso");
            dto.setCreateDate(ANTES);
            dto.setCreateUser("intruso");
            return dto;
        }

        @Test
        @DisplayName("se encuentran los mappers generados, también los tres con tipo padre")
        void seEncuentran() {
            assertThat(lovMappers()).extracting(Named::getPayload)
                    .contains(ProfileStatusMapperImpl.class, FoundationMapperImpl.class, PortalMapperImpl.class,
                            AnchorageFoundationMapperImpl.class);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapperTest#lovMappers")
        @DisplayName("el alta no copia del cuerpo el id, la versión ni la auditoría")
        void alta(Class<?> impl) throws ReflectiveOperationException {
            Lov entity = instancia(impl).toEntity(cuerpo(impl));

            assertThat(entity.getCode()).isEqualTo("NUEVO");
            assertThat(entity.getId()).isNull();
            assertThat(entity.getVersionNumber()).isEqualTo(1);
            assertThat(entity.getVersionDate()).isNull();
            assertThat(entity.getVersionUser()).isNull();
            assertThat(entity.getCreateDate()).isNull();
            assertThat(entity.getCreateUser()).isNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapperTest#lovMappers")
        @DisplayName("la modificación vuelca el cuerpo sin tocar el id, la versión ni la auditoría de la fila")
        void modificacion(Class<?> impl) throws ReflectiveOperationException {
            LocalDateTime creada = LocalDateTime.of(2026, 8, 1, 10, 0);
            LocalDateTime modificada = LocalDateTime.of(2026, 9, 1, 10, 0);
            Lov fila = (Lov) nueva(toEntity(impl).getReturnType());
            fila.setId(5L);
            fila.setCode("VIEJO");
            fila.setVersionNumber(4);
            fila.setVersionDate(modificada);
            fila.setVersionUser("luis");
            fila.setCreateDate(creada);
            fila.setCreateUser("ana");

            instancia(impl).updateEntityFromDTO(cuerpo(impl), fila);

            assertThat(fila.getCode()).isEqualTo("NUEVO");
            assertThat(fila.getId()).isEqualTo(5L);
            assertThat(fila.getVersionNumber()).isEqualTo(4);
            assertThat(fila.getVersionDate()).isEqualTo(modificada);
            assertThat(fila.getVersionUser()).isEqualTo("luis");
            assertThat(fila.getCreateDate()).isEqualTo(creada);
            assertThat(fila.getCreateUser()).isEqualTo("ana");
        }
    }

    @Nested
    @DisplayName("findOrMap")
    class FindOrMap {

        @Test
        @DisplayName("con id existente se reutiliza la fila, sin consultar por codigo")
        void porId() {
            ProfileStatus existente = entity(1L, "OK");
            when(repository.findById(1L)).thenReturn(Optional.of(existente));

            assertThat(mapper.findOrMap(dto(1L, "OTRO"), repository)).isSameAs(existente);

            verify(repository, never()).findByCode("OTRO");
        }

        @Test
        @DisplayName("si el id no existe se intenta por codigo")
        void respaldoPorCodigo() {
            ProfileStatus existente = entity(2L, "OK");
            when(repository.findById(1L)).thenReturn(Optional.empty());
            when(repository.findByCode("OK")).thenReturn(existente);

            assertThat(mapper.findOrMap(dto(1L, "OK"), repository)).isSameAs(existente);
        }

        @Test
        @DisplayName("sin id se busca directamente por codigo")
        void soloCodigo() {
            ProfileStatus existente = entity(2L, "OK");
            when(repository.findByCode("OK")).thenReturn(existente);

            assertThat(mapper.findOrMap(dto(null, "OK"), repository)).isSameAs(existente);

            verify(repository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("si no se encuentra nada se mapea una entidad nueva")
        void mapeaNueva() {
            // Es la rama que crea catalogo desde una peticion: la entidad sale sin id, lista para
            // insertarse.
            when(repository.findByCode("NUEVO")).thenReturn(null);

            ProfileStatus resultado = mapper.findOrMap(dto(null, "NUEVO"), repository);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isNull();
            assertThat(resultado.getCode()).isEqualTo("NUEVO");
        }

        @Test
        @DisplayName("un codigo en blanco no se busca, se mapea directamente")
        void codigoEnBlanco() {
            ProfileStatus resultado = mapper.findOrMap(dto(null, "  "), repository);

            assertThat(resultado).isNotNull();
            verify(repository, never()).findByCode("  ");
        }

        @Test
        @DisplayName("un DTO nulo devuelve nulo sin consultar")
        void dtoNulo() {
            assertThat(mapper.findOrMap(null, repository)).isNull();

            verifyNoInteractions(repository);
        }
    }
}
