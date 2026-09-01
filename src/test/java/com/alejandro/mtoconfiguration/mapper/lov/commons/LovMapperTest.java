package com.alejandro.mtoconfiguration.mapper.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import com.alejandro.mtoconfiguration.mapper.lov.ProfileStatusMapperImpl;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.ProfileStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
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
