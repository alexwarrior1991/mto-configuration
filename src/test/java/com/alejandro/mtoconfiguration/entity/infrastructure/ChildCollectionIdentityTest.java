package com.alejandro.mtoconfiguration.entity.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un padre puede llegar con VARIOS hijos nuevos en la misma lista, y todos tienen que
 * sobrevivir al mapeo. Es el escenario que fija {@code ChildIdentityTest} en la capa de
 * validacion; estos tests lo fijan en la capa de entidad, que es donde se perdian.
 * <p>
 * No es un caso teorico: los mappers generados por MapStruct anaden los hijos UNO A UNO
 * con los adders del padre ({@code station.addSectionInsulator(...)},
 * {@code profile.addCantilever(...)}), asi que un alta anidada pasa exactamente por
 * aqui. Con dos hijos nuevos, ambos ids son null; si equals los da por iguales, el
 * {@code contains} del adder (y el propio {@code HashSet}, en el caso de Station)
 * descarta el segundo EN SILENCIO y se persiste uno menos de los que mando el cliente.
 */
class ChildCollectionIdentityTest {

    @Test
    @DisplayName("una estacion admite dos aisladores nuevos a la vez")
    void dosSectionInsulatorsNuevosSobrevivenAlAdder() {
        Station station = new Station();
        station.setName("Estacion");

        station.addSectionInsulator(sectionInsulator("Aislador 1"));
        station.addSectionInsulator(sectionInsulator("Aislador 2"));

        assertThat(station.getSectionInsulators())
                .extracting(SectionInsulator::getName)
                .containsExactlyInAnyOrder("Aislador 1", "Aislador 2");
    }

    @Test
    @DisplayName("un perfil admite dos meniscos nuevos a la vez")
    void dosCantileversNuevosSobrevivenAlAdder() {
        Profile profile = new Profile();
        profile.setProfileId("P-001");

        profile.addCantilever(cantilever("5.500"));
        profile.addCantilever(cantilever("6.500"));

        assertThat(profile.getCantilevers())
                .extracting(Cantilever::getCwHeight)
                .containsExactly(new BigDecimal("5.500"), new BigDecimal("6.500"));
    }

    @Test
    @DisplayName("dos entidades nuevas distintas no son iguales entre si")
    void dosEntidadesNuevasNoSonIguales() {
        assertThat(sectionInsulator("Aislador 1")).isNotEqualTo(sectionInsulator("Aislador 2"));
        assertThat(cantilever("5.500")).isNotEqualTo(cantilever("6.500"));
    }

    /**
     * La otra mitad del contrato: una entidad ya persistida se identifica por su id, que
     * es lo que permite reconocerla entre dos cargas distintas del mismo registro.
     */
    @Test
    @DisplayName("dos instancias del mismo registro persistido son iguales")
    void dosInstanciasDelMismoRegistroSonIguales() {
        SectionInsulator uno = sectionInsulator("Aislador 1");
        uno.setId(7L);
        SectionInsulator otro = sectionInsulator("Aislador 1");
        otro.setId(7L);

        assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);

        Cantilever menisco = cantilever("5.500");
        menisco.setId(9L);
        Cantilever mismoMenisco = cantilever("5.500");
        mismoMenisco.setId(9L);

        assertThat(menisco).isEqualTo(mismoMenisco).hasSameHashCodeAs(mismoMenisco);
    }

    /**
     * El hashCode no puede cambiar cuando la entidad pasa de nueva a persistida: si
     * cambia, un hijo metido en un HashSet antes del flush queda en el cubo equivocado y
     * deja de encontrarse con {@code contains} o {@code remove}.
     */
    @Test
    @DisplayName("el hashCode no cambia al asignarse el id")
    void elHashCodeNoCambiaAlPersistir() {
        Cantilever cantilever = cantilever("5.500");
        int antes = cantilever.hashCode();
        cantilever.setId(9L);

        assertThat(cantilever.hashCode()).isEqualTo(antes);
    }

    private SectionInsulator sectionInsulator(String name) {
        SectionInsulator sectionInsulator = new SectionInsulator();
        sectionInsulator.setName(name);
        sectionInsulator.setEnabled(Boolean.TRUE);
        return sectionInsulator;
    }

    private Cantilever cantilever(String cwHeight) {
        Cantilever cantilever = new Cantilever();
        cantilever.setCwHeight(new BigDecimal(cwHeight));
        return cantilever;
    }
}
