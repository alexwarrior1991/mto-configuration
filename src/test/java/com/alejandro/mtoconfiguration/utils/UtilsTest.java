package com.alejandro.mtoconfiguration.utils;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Predicados de {@link Utils}.
 *
 * <p>Parecen triviales pero gobiernan las ramas de {@code BaseService}: {@code hasErrors} decide si
 * una operacion aborta y {@code exists} si un DTO se trata como alta o como modificacion. Una
 * alerta de aviso que bloquease un guardado, o un {@code null} que se colase como "existe", son
 * fallos que no se ven en el sitio donde se producen.</p>
 */
class UtilsTest {

    private static class TestDTO extends BaseDTO {
        TestDTO(Long id) {
            setId(id);
        }
    }

    @Nested
    @DisplayName("Deteccion de errores")
    class DeteccionDeErrores {

        @Test
        @DisplayName("solo una alerta de nivel DANGER cuenta como error")
        void soloDanger() {
            assertThat(Utils.hasErrors(List.of(Alert.ofDanger("error")))).isTrue();
            assertThat(Utils.hasErrors(List.of(Alert.ofWarning("aviso")))).isFalse();
            assertThat(Utils.hasErrors(List.of(Alert.ofInfo("info")))).isFalse();
            assertThat(Utils.hasErrors(List.of(Alert.ofSuccess("ok")))).isFalse();
        }

        @Test
        @DisplayName("basta con una DANGER entre varias alertas de otro nivel")
        void mezcla() {
            assertThat(Utils.hasErrors(List.of(
                    Alert.ofInfo("info"), Alert.ofWarning("aviso"), Alert.ofDanger("error"))))
                    .isTrue();
        }

        @Test
        @DisplayName("una lista nula o vacia no es un error")
        void listaVacia() {
            assertThat(Utils.hasErrors(null)).isFalse();
            assertThat(Utils.hasErrors(Collections.emptyList())).isFalse();
        }
    }

    @Nested
    @DisplayName("Estado del DTO")
    class EstadoDelDTO {

        @Test
        @DisplayName("un DTO sin id es nuevo y uno con id ya existe")
        void nuevoOExistente() {
            assertThat(Utils.isNew(new TestDTO(null))).isTrue();
            assertThat(Utils.isNotNew(new TestDTO(null))).isFalse();
            assertThat(Utils.exists(new TestDTO(1L))).isTrue();
            assertThat(Utils.notExists(new TestDTO(1L))).isFalse();
        }

        @Test
        @DisplayName("un DTO nulo no es nuevo ni existe")
        void dtoNulo() {
            // Es la asimetria que hace que create(null) y update(null) fallen por caminos distintos
            // en BaseService, asi que conviene tenerla escrita.
            assertThat(Utils.isNew(null)).isFalse();
            assertThat(Utils.exists(null)).isFalse();
            assertThat(Utils.notExists(null)).isTrue();
            assertThat(Utils.isNotNew(null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Copia de propiedades")
    class CopiaDePropiedades {

        @Test
        @DisplayName("las propiedades de auditoria se copian de un DTO a otro")
        void auditoria() {
            TestDTO source = new TestDTO(1L);
            source.setCreateUser("ana");
            source.setVersionUser("luis");
            source.setCreateDate(LocalDateTime.of(2026, 1, 1, 10, 0));
            source.setVersionDate(LocalDateTime.of(2026, 2, 2, 11, 0));
            source.setVersionNumber(3);

            TestDTO target = new TestDTO(2L);
            Utils.mapAuditProperties(source, target);

            assertThat(target.getCreateUser()).isEqualTo("ana");
            assertThat(target.getVersionUser()).isEqualTo("luis");
            assertThat(target.getCreateDate()).isEqualTo(source.getCreateDate());
            assertThat(target.getVersionDate()).isEqualTo(source.getVersionDate());
            assertThat(target.getVersionNumber()).isEqualTo(3);
            assertThat(target.getId()).as("el id no es una propiedad de auditoria").isEqualTo(2L);
        }

        @Test
        @DisplayName("copiar desde o hacia un nulo no rompe")
        void copiaConNulos() {
            TestDTO dto = new TestDTO(1L);

            Utils.mapAuditProperties(null, dto);
            Utils.mapAuditProperties(dto, null);
            Utils.mapLovProperties(null, new LovDTO());
            Utils.mapLovProperties(new LovDTO(), null);

            assertThat(dto.getCreateUser()).isNull();
        }

        @Test
        @DisplayName("las propiedades de una LOV se copian de un DTO a otro")
        void propiedadesLov() {
            LovDTO source = new LovDTO();
            source.setCode("A");
            source.setDescription("Descripcion");
            source.setType("ProfileStatusDTO");
            source.setEnabled(true);
            source.setVersionNumber(2);

            LovDTO target = new LovDTO();
            Utils.mapLovProperties(source, target);

            assertThat(target.getCode()).isEqualTo("A");
            assertThat(target.getDescription()).isEqualTo("Descripcion");
            assertThat(target.getType()).isEqualTo("ProfileStatusDTO");
            assertThat(target.isEnabled()).isTrue();
            assertThat(target.getVersionNumber()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Filtros y fechas")
    class FiltrosYFechas {

        @Test
        @DisplayName("distinctByKey deja pasar el primer elemento de cada clave")
        void distinctByKey() {
            List<String> filtered = new ArrayList<>(List.of("uno", "dos", "tres", "cuatro"))
                    .stream()
                    .filter(Utils.distinctByKey(String::length))
                    .toList();

            assertThat(filtered).containsExactly("uno", "tres", "cuatro");
        }

        @Test
        @DisplayName("truncar una fecha nula devuelve nulo")
        void fechasNulas() {
            assertThat(Utils.truncateDateToDay(null)).isNull();
            assertThat(Utils.truncateDateToMinutes(null)).isNull();
            assertThat(Utils.getAsLocalDateTimeTruncatedToMinutes(null)).isNull();
        }

        @Test
        @DisplayName("truncar a minutos descarta segundos y milisegundos")
        void truncadoAMinutos() {
            java.util.Date date = java.util.Date.from(
                    java.time.Instant.parse("2026-03-04T10:15:37.512Z"));

            assertThat(Utils.truncateDateToMinutes(date).toInstant())
                    .isEqualTo(java.time.Instant.parse("2026-03-04T10:15:00Z"));
        }

        @Test
        @DisplayName("truncar a dia descarta la hora")
        void truncadoADia() {
            java.util.Date date = java.util.Date.from(
                    java.time.Instant.parse("2026-03-04T10:15:37Z"));

            assertThat(Utils.truncateDateToDay(date).toInstant())
                    .isEqualTo(java.time.Instant.parse("2026-03-04T00:00:00Z"));
        }
    }
}
