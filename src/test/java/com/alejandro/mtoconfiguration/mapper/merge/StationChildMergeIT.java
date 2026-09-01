package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.StationMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliacion de las tres colecciones de una estacion, contra la base de datos.
 *
 * <p>Aqui las colecciones son {@code Set} y no {@code List}, asi que la identidad de los hijos
 * entra en juego dos veces: al fusionar por id y al meterlos en el conjunto. Y son tres a la vez,
 * de modo que una sola peticion tiene que resolver altas, bajas y modificaciones en las tres sin
 * que una interfiera con otra.</p>
 */
class StationChildMergeIT extends AbstractChildMergeIT {

    @Autowired
    private StationMapper mapper;

    private Long estacionId;
    private Long primeraViaId;
    private Long segundaViaId;
    private Long seccionadorId;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = new ExecutionPackage();
        paquete.setName("PAQUETE 1");
        paquete.setInitialPackage(false);
        paquete.setLength(1000L);
        paquete.setStartDate(LocalDate.of(2026, 1, 1));
        paquete.setEndDate(LocalDate.of(2026, 12, 31));
        paquete.setEnabled(true);
        em.persist(paquete);

        Station estacion = new Station();
        estacion.setName("ATOCHA");
        estacion.setExecutionPackage(paquete);
        em.persist(estacion);

        Track primera = via("VIA 1", estacion, paquete);
        Track segunda = via("VIA 2", estacion, paquete);
        Disconnector seccionador = seccionador("SECC-1", estacion);

        flushAndClear();

        estacionId = estacion.getId();
        primeraViaId = primera.getId();
        segundaViaId = segunda.getId();
        seccionadorId = seccionador.getId();
    }

    private Track via(String nombre, Station estacion, ExecutionPackage paquete) {
        Track via = new Track();
        via.setName(nombre);
        via.setEnabled(true);
        via.setStation(estacion);
        via.setExecutionPackage(paquete);
        em.persist(via);
        return via;
    }

    private Disconnector seccionador(String nombre, Station estacion) {
        Disconnector seccionador = new Disconnector();
        seccionador.setName(nombre);
        seccionador.setOnLoad(true);
        seccionador.setStation(estacion);
        em.persist(seccionador);
        return seccionador;
    }

    private static TrackDTO viaDto(Long id, String nombre) {
        TrackDTO dto = new TrackDTO();
        dto.setId(id);
        dto.setName(nombre);
        dto.setEnabled(true);
        return dto;
    }

    private static DisconnectorDTO seccionadorDto(Long id, String nombre) {
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setId(id);
        dto.setName(nombre);
        dto.setOnLoad(true);
        return dto;
    }

    private static SectionInsulatorDTO aisladorDto(Long id, String nombre) {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setId(id);
        dto.setName(nombre);
        dto.setEnabled(true);
        return dto;
    }

    private StationDTO peticion(List<TrackDTO> vias, List<DisconnectorDTO> seccionadores,
                                List<SectionInsulatorDTO> aisladores) {
        StationDTO dto = new StationDTO();
        dto.setId(estacionId);
        dto.setName("ATOCHA");
        dto.setTracks(new ArrayList<>(vias));
        dto.setDisconnectors(new ArrayList<>(seccionadores));
        dto.setSectionInsulators(new ArrayList<>(aisladores));
        return dto;
    }

    private void aplicar(StationDTO dto) {
        Station gestionada = em.find(Station.class, estacionId);
        mapper.updateEntityFromDTO(dto, gestionada);
        flushAndClear();
    }

    @Test
    @DisplayName("renombrar una via actualiza SU fila y no inserta ninguna")
    void editarNoDuplica() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA PRINCIPAL"), viaDto(segundaViaId, "VIA 2")),
                List.of(seccionadorDto(seccionadorId, "SECC-1")),
                List.of()));

        assertThat(contarFilas("track")).isEqualTo(2);
        assertThat(em.find(Track.class, primeraViaId).getName()).isEqualTo("VIA PRINCIPAL");
    }

    @Test
    @DisplayName("guardar tres veces seguidas deja las mismas filas")
    void guardadosRepetidosNoAcumulan() {
        for (int i = 0; i < 3; i++) {
            aplicar(peticion(
                    List.of(viaDto(primeraViaId, "VIA " + i), viaDto(segundaViaId, "VIA 2")),
                    List.of(seccionadorDto(seccionadorId, "SECC-1")),
                    List.of()));
        }

        assertThat(contarFilas("track")).isEqualTo(2);
        assertThat(contarFilas("disconnector")).isEqualTo(1);
    }

    @Test
    @DisplayName("una sola peticion resuelve las tres colecciones a la vez")
    void reconciliacionDeLasTres() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA PRINCIPAL"),   // editada
                        viaDto(null, "VIA 3")),                  // nueva; la segunda no viene
                List.of(seccionadorDto(seccionadorId, "SECC-RENOMBRADO")),
                List.of(aisladorDto(null, "AISL-1"))));          // aislador nuevo

        assertThat(contarFilas("track")).isEqualTo(2);
        assertThat(em.find(Track.class, primeraViaId).getName()).isEqualTo("VIA PRINCIPAL");
        assertThat(em.find(Track.class, segundaViaId)).isNull();
        assertThat(em.find(Disconnector.class, seccionadorId).getName()).isEqualTo("SECC-RENOMBRADO");
        assertThat(contarFilas("section_insulator")).isEqualTo(1);
    }

    @Test
    @DisplayName("el hijo nuevo se guarda con la clave ajena a su estacion")
    void hijoNuevoQuedaVinculado() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA 1"), viaDto(segundaViaId, "VIA 2")),
                List.of(seccionadorDto(seccionadorId, "SECC-1")),
                List.of(aisladorDto(null, "AISL-1"))));

        Station releida = em.find(Station.class, estacionId);

        assertThat(releida.getSectionInsulators())
                .singleElement()
                .satisfies(aislador -> assertThat(aislador.getStation().getId()).isEqualTo(estacionId));
    }

    @Test
    @DisplayName("vaciar una coleccion borra sus filas sin tocar las otras dos")
    void vaciarUnaSolaColeccion() {
        aplicar(peticion(
                List.of(),
                List.of(seccionadorDto(seccionadorId, "SECC-1")),
                List.of()));

        assertThat(contarFilas("track")).isZero();
        assertThat(contarFilas("disconnector")).as("el seccionador sigue ahi").isEqualTo(1);
    }

    @Test
    @DisplayName("un aislador editado conserva su id entre guardados")
    void elIdSobrevive() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA 1"), viaDto(segundaViaId, "VIA 2")),
                List.of(seccionadorDto(seccionadorId, "SECC-1")),
                List.of(aisladorDto(null, "AISL-1"))));

        Station conAislador = em.find(Station.class, estacionId);
        SectionInsulator aislador = conAislador.getSectionInsulators().iterator().next();
        Long aisladorId = aislador.getId();
        flushAndClear();

        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA 1"), viaDto(segundaViaId, "VIA 2")),
                List.of(seccionadorDto(seccionadorId, "SECC-1")),
                List.of(aisladorDto(aisladorId, "AISL-RENOMBRADO"))));

        assertThat(contarFilas("section_insulator")).isEqualTo(1);
        assertThat(em.find(SectionInsulator.class, aisladorId).getName()).isEqualTo("AISL-RENOMBRADO");
    }
}
