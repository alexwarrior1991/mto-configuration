package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.mapper.infraestructure.SectionInsulatorMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Reconciliacion de las agujas de un aislador de seccion, contra la base de datos.
 *
 * <p>Los tests unitarios del mapper demuestran que el objeto queda bien en memoria. Lo que solo se
 * ve contra PostgreSQL es lo que importa aqui: que el UPDATE caiga en la fila correcta, que
 * {@code orphanRemoval} borre exactamente la aguja que se quito, que no se cuele ningun INSERT de
 * mas, y que dos agujas nuevas en la misma peticion sobrevivan las dos —el fallo que el equals
 * constante de {@code SectionInsulatorSwitch} existe para evitar—.
 */
class SectionInsulatorChildMergeIT extends AbstractChildMergeIT {

    @Autowired
    private SectionInsulatorMapper mapper;

    private Long aisladorId;
    private Long primeraAgujaId;
    private Long segundaAgujaId;
    private Long viaId;
    private Long viaConectadaId;
    private Long estacionId;

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

        Track via = via("VIA 1", paquete);
        Track viaConectada = via("VIA 2", paquete);

        SectionInsulator aislador = new SectionInsulator();
        aislador.setName("AISL-1");
        aislador.setEnabled(true);
        aislador.setStation(estacion);
        aislador.setKp(new BigDecimal("110176.000"));
        aislador.setInstallationType(SectionInsulatorInstallationType.TRACK_CONNECTION);
        aislador.setTrack(via);
        aislador.setConnectedTrack(viaConectada);

        SectionInsulatorSwitch primera = aguja("W31", "110176.000", 9, via);
        SectionInsulatorSwitch segunda = aguja("W41", "110249.000", 9, viaConectada);
        aislador.addSwitch(primera);
        aislador.addSwitch(segunda);
        em.persist(aislador);

        flushAndClear();

        aisladorId = aislador.getId();
        primeraAgujaId = primera.getId();
        segundaAgujaId = segunda.getId();
        viaId = via.getId();
        viaConectadaId = viaConectada.getId();
        estacionId = estacion.getId();
    }

    private Track via(String nombre, ExecutionPackage paquete) {
        Track via = new Track();
        via.setName(nombre);
        via.setEnabled(true);
        via.setExecutionPackage(paquete);
        em.persist(via);
        return via;
    }

    private static SectionInsulatorSwitch aguja(String codigo, String kp, Integer tangente, Track via) {
        SectionInsulatorSwitch aguja = new SectionInsulatorSwitch();
        aguja.setCode(codigo);
        aguja.setKp(new BigDecimal(kp));
        aguja.setTurnoutDenominator(tangente);
        aguja.setTrack(via);
        aguja.setEnabled(true);
        return aguja;
    }

    private SectionInsulatorSwitchDTO agujaDto(Long id, String codigo, String kp, Integer tangente) {
        SectionInsulatorSwitchDTO dto = new SectionInsulatorSwitchDTO();
        dto.setId(id);
        dto.setCode(codigo);
        dto.setKp(new BigDecimal(kp));
        dto.setTurnoutDenominator(tangente);
        dto.setTrackId(viaId);
        dto.setEnabled(true);
        return dto;
    }

    private SectionInsulatorDTO peticion(List<SectionInsulatorSwitchDTO> agujas) {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setId(aisladorId);
        dto.setName("AISL-1");
        dto.setEnabled(true);
        dto.setStationId(estacionId);
        dto.setKp(new BigDecimal("110176.000"));
        dto.setInstallationType(SectionInsulatorInstallationType.TRACK_CONNECTION);
        dto.setTrackId(viaId);
        dto.setConnectedTrackId(viaConectadaId);
        dto.setSwitches(new ArrayList<>(agujas));
        return dto;
    }

    private void aplicar(SectionInsulatorDTO dto) {
        SectionInsulator gestionado = em.find(SectionInsulator.class, aisladorId);
        mapper.updateEntityFromDTO(dto, gestionado);
        flushAndClear();
    }

    @Test
    @DisplayName("cambiar la tangente de una aguja actualiza SU fila y no inserta ninguna")
    void editarNoDuplica() {
        aplicar(peticion(List.of(
                agujaDto(primeraAgujaId, "W31", "110176.000", 12),
                agujaDto(segundaAgujaId, "W41", "110249.000", 9))));

        assertThat(contarFilas("section_insulator_switch")).isEqualTo(2);
        assertThat(em.find(SectionInsulatorSwitch.class, primeraAgujaId).getTurnoutDenominator())
                .isEqualTo(12);
    }

    @Test
    @DisplayName("guardar tres veces seguidas deja las mismas filas")
    void guardadosRepetidosNoAcumulan() {
        for (int i = 0; i < 3; i++) {
            aplicar(peticion(List.of(
                    agujaDto(primeraAgujaId, "W31", "110176.000", 9),
                    agujaDto(segundaAgujaId, "W41", "110249.000", 9))));
        }

        assertThat(contarFilas("section_insulator_switch")).isEqualTo(2);
    }

    @Test
    @DisplayName("la aguja que no viene en la peticion se borra: orphanRemoval")
    void laAgujaQueNoVieneSeBorra() {
        aplicar(peticion(List.of(agujaDto(primeraAgujaId, "W31", "110176.000", 9))));

        assertThat(contarFilas("section_insulator_switch")).isEqualTo(1);
        assertThat(em.find(SectionInsulatorSwitch.class, segundaAgujaId)).isNull();
    }

    @Test
    @DisplayName("dos agujas nuevas en la misma peticion sobreviven las dos")
    void dosAgujasNuevasALaVez() {
        aplicar(peticion(List.of(
                agujaDto(primeraAgujaId, "W31", "110176.000", 9),
                agujaDto(segundaAgujaId, "W41", "110249.000", 9),
                agujaDto(null, "W45", "110315.000", 8),
                agujaDto(null, "W47", "110339.000", 8))));

        assertThat(contarFilas("section_insulator_switch")).isEqualTo(4);
    }

    @Test
    @DisplayName("la aguja nueva queda con la clave ajena a su aislador")
    void agujaNuevaQuedaVinculada() {
        aplicar(peticion(List.of(
                agujaDto(primeraAgujaId, "W31", "110176.000", 9),
                agujaDto(segundaAgujaId, "W41", "110249.000", 9),
                agujaDto(null, "W45", "110315.000", 8))));

        SectionInsulator releido = em.find(SectionInsulator.class, aisladorId);

        assertThat(releido.getSwitches())
                .hasSize(3)
                .allSatisfy(aguja -> assertThat(aguja.getSectionInsulator().getId()).isEqualTo(aisladorId));
    }

    @Test
    @DisplayName("la coleccion se lee en orden de KP, que es el fisico a lo largo de la via")
    void seLeenEnOrdenDeKp() {
        aplicar(peticion(List.of(
                agujaDto(primeraAgujaId, "W31", "110249.000", 9),   // ahora va DESPUES
                agujaDto(segundaAgujaId, "W41", "110176.000", 9))));

        assertThat(em.find(SectionInsulator.class, aisladorId).getSwitches())
                .extracting(SectionInsulatorSwitch::getCode, SectionInsulatorSwitch::getKp)
                .containsExactly(
                        tuple("W41", new BigDecimal("110176.000")),
                        tuple("W31", new BigDecimal("110249.000")));
    }

    @Test
    @DisplayName("una lista vacia deja al aislador sin agujas")
    void listaVaciaBorraTodas() {
        aplicar(peticion(List.of()));

        assertThat(contarFilas("section_insulator_switch")).isZero();
    }
}
