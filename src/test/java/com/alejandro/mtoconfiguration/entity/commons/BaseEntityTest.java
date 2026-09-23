package com.alejandro.mtoconfiguration.entity.commons;

import com.alejandro.mtoconfiguration.core.exception.ConcurrencyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloqueo optimista de {@link BaseEntity#validateVersion}.
 *
 * <p>Es la comprobacion que hacen el servicio (sobre el padre) y los mappers (sobre cada hijo que
 * viaja con su id) antes de volcar un DTO. El caso que importa es el {@code null}: significa "no
 * comprobar", porque el importador del maestro y los trabajos escriben sin version a proposito.
 * Hasta ahora contaba como version 1, de modo que cualquier fila editada una vez habria rechazado
 * todas sus escrituras.</p>
 */
class BaseEntityTest {

    private static final class Fila extends BaseEntity {

        Fila(Long id, Integer version) {
            this.id = id;
            setVersionNumber(version);
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    @Test
    @DisplayName("la version que se leyo deja escribir")
    void versionAlDia() {
        assertThatCode(() -> new Fila(7L, 3).validateVersion(3)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sin version no se comprueba nada, aunque la fila vaya por la version 5")
    void sinVersion() {
        assertThatCode(() -> new Fila(7L, 5).validateVersion(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("otra version es un conflicto, y el mensaje dice que fila y que versiones")
    void versionDesactualizada() {
        assertThatThrownBy(() -> new Fila(7L, 3).validateVersion(2))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessage("Fila 7 ha cambiado desde que se leyo: llego la version 2 y va por la 3");
    }
}
