package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El id resuelto es la mitad del {@code referenceId} y del {@code aggregateId} del
 * outbox: si se pierde aqui, el mensaje no se puede correlacionar con la entidad que
 * lo origino.
 */
class MasterDataEntityIdResolverTest {

    private final MasterDataEntityIdResolver resolver = new MasterDataEntityIdResolver();

    private static class ConIdPropio {
        @Id
        private Long id;

        ConIdPropio(Long id) {
            this.id = id;
        }
    }

    private static class ConIdHeredado extends ConIdPropio {
        ConIdHeredado(Long id) {
            super(id);
        }
    }

    private static class SinNingunCampoId {
        private Long id;

        SinNingunCampoId(Long id) {
            this.id = id;
        }
    }

    @Test
    void devuelveElValorDelCampoAnotadoConId() {
        assertThat(resolver.resolve(new ConIdPropio(42L))).isEqualTo("42");
    }

    @Test
    void encuentraElCampoIdAunqueEsteDeclaradoEnUnaSuperclase() {
        assertThat(resolver.resolve(new ConIdHeredado(7L))).isEqualTo("7");
    }

    @Test
    void leeElCampoIdAunqueSeaPrivado() {
        // El campo es privado a proposito: el resolver depende de field.setAccessible(true)
        // para poder leerlo sin necesitar un getter publico.
        assertThat(resolver.resolve(new ConIdPropio(1L))).isEqualTo("1");
    }

    @Test
    void unaEntidadSinCampoIdEnTodaLaJerarquiaFallaConUnMensajeQueNombraLaClase() {
        assertThatThrownBy(() -> resolver.resolve(new SinNingunCampoId(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Id")
                .hasMessageContaining("SinNingunCampoId");
    }

    @Test
    void unIdNuloEsUnErrorDeProgramacionYNoUnaCadenaVacia() {
        assertThatThrownBy(() -> resolver.resolve(new ConIdPropio(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ConIdPropio");
    }

    @Test
    void unaEntidadNulaEsUnErrorDeProgramacion() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * BUG DETECTADO POR ESTE TEST, no comportamiento deseado.
     * <p>
     * Todas las entidades reales de datos maestros (Cantilever, Disconnector,
     * ExecutionPackage, Profile, SectionInsulator, Station, SteadyArm, Track) declaran
     * {@code @Id} sobre el GETTER (ver {@code CantileverRepository}, que ya documenta
     * "acceso por propiedad (@Id sobre el getter)"), no sobre el campo. Pero
     * {@code MasterDataEntityIdResolver} solo mira {@code Class#getDeclaredFields()} y
     * pregunta {@code field.isAnnotationPresent(Id.class)}: una anotacion puesta en un
     * metodo NUNCA aparece ahi, son elementos reflejados distintos en la JVM.
     * <p>
     * Resultado real hoy: con {@code app.rabbitmq.enabled: true} (el valor por
     * defecto), crear/modificar/borrar CUALQUIER entidad publicable lanza esta
     * excepcion dentro de {@code MasterDataEntityChangedEventListener}, en la misma
     * transaccion de negocio. Este test fija ese comportamiento para que no pase
     * desapercibido; si se corrige el resolver (por ejemplo tambien mirando los
     * metodos, o el {@code getId()} de {@code IEntity}), este test debe reescribirse
     * para esperar {@code "1"} en lugar de la excepcion.
     */
    @Test
    void unaEntidadRealConIdEnElGetterFallaHoyPorqueElResolverSoloMiraCampos() {
        Cantilever cantilever = new Cantilever();
        cantilever.setId(1L);

        assertThatThrownBy(() -> resolver.resolve(cantilever))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@Id")
                .hasMessageContaining("Cantilever");
    }
}
