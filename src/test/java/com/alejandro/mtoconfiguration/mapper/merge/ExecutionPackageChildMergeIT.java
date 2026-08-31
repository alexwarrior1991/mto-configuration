package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ExecutionPackageMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconciliacion de las vias y estaciones de un paquete, contra la base de datos.
 *
 * <p>Es la raiz del arbol de infraestructura: paquete, via, perfil. Tres niveles de reconciliacion
 * encadenados en una sola peticion, que es el escenario mas profundo del proyecto y el que peor
 * sale si algun nivel vuelve a añadir en lugar de fusionar.</p>
 */
class ExecutionPackageChildMergeIT extends AbstractChildMergeIT {

    @Autowired
    private ExecutionPackageMapper mapper;

    private Long paqueteId;
    private Long primeraViaId;
    private Long segundaViaId;
    private Long estacionId;
    private Long perfilId;

    @BeforeEach
    void seed() {
        ExecutionPackage paquete = new ExecutionPackage();
        paquete.setName("PAQUETE NORTE");
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

        Track primera = new Track();
        primera.setName("VIA 1");
        primera.setEnabled(true);
        primera.setExecutionPackage(paquete);
        em.persist(primera);

        Track segunda = new Track();
        segunda.setName("VIA 2");
        segunda.setEnabled(true);
        segunda.setExecutionPackage(paquete);
        em.persist(segunda);

        // Por el adder del padre, que mantiene los dos lados de la relacion en memoria.
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        primera.addProfile(perfil);
        em.persist(perfil);

        flushAndClear();

        paqueteId = paquete.getId();
        primeraViaId = primera.getId();
        segundaViaId = segunda.getId();
        estacionId = estacion.getId();
        perfilId = perfil.getId();
    }

    private static TrackDTO viaDto(Long id, String nombre) {
        TrackDTO dto = new TrackDTO();
        dto.setId(id);
        dto.setName(nombre);
        dto.setEnabled(true);
        return dto;
    }

    private static StationDTO estacionDto(Long id, String nombre) {
        StationDTO dto = new StationDTO();
        dto.setId(id);
        dto.setName(nombre);
        return dto;
    }

    private ExecutionPackageDTO peticion(List<TrackDTO> vias, List<StationDTO> estaciones) {
        ExecutionPackageDTO dto = new ExecutionPackageDTO();
        dto.setId(paqueteId);
        dto.setName("PAQUETE NORTE");
        dto.setInitialPackage(false);
        dto.setLength(1000L);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(LocalDate.of(2026, 12, 31));
        dto.setEnabled(true);
        dto.setTracks(new ArrayList<>(vias));
        dto.setStations(new ArrayList<>(estaciones));
        return dto;
    }

    private void aplicar(ExecutionPackageDTO dto) {
        ExecutionPackage gestionado = em.find(ExecutionPackage.class, paqueteId);
        mapper.updateEntityFromDTO(dto, gestionado);
        flushAndClear();
    }

    @Test
    @DisplayName("renombrar una via actualiza SU fila y no inserta ninguna")
    void editarNoDuplica() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA PRINCIPAL"), viaDto(segundaViaId, "VIA 2")),
                List.of(estacionDto(estacionId, "ATOCHA"))));

        assertThat(contarFilas("track")).isEqualTo(2);
        assertThat(em.find(Track.class, primeraViaId).getName()).isEqualTo("VIA PRINCIPAL");
    }

    @Test
    @DisplayName("guardar tres veces seguidas no acumula filas en ningun nivel")
    void guardadosRepetidosNoAcumulan() {
        for (int i = 0; i < 3; i++) {
            TrackDTO primera = viaDto(primeraViaId, "VIA " + i);
            primera.setProfiles(new ArrayList<>(List.of(perfilDto(perfilId, "P-001", "10.000"))));

            aplicar(peticion(
                    List.of(primera, viaDto(segundaViaId, "VIA 2")),
                    List.of(estacionDto(estacionId, "ATOCHA"))));
        }

        assertThat(contarFilas("track")).isEqualTo(2);
        assertThat(contarFilas("station")).isEqualTo(1);
        assertThat(contarFilas("profile")).as("tres niveles abajo tampoco crece").isEqualTo(1);
    }

    private static ProfileDTO perfilDto(Long id, String profileId, String kp) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        dto.setProfileId(profileId);
        dto.setKp(kp);
        return dto;
    }

    @Test
    @DisplayName("la reconciliacion atraviesa paquete, via y perfil en una sola peticion")
    void reconciliacionDeTresNiveles() {
        TrackDTO primera = viaDto(primeraViaId, "VIA PRINCIPAL");
        primera.setProfiles(new ArrayList<>(List.of(perfilDto(perfilId, "P-001-BIS", "11.000"))));

        aplicar(peticion(
                List.of(primera, viaDto(segundaViaId, "VIA 2")),
                List.of(estacionDto(estacionId, "ATOCHA"))));

        assertThat(em.find(Track.class, primeraViaId).getName()).isEqualTo("VIA PRINCIPAL");
        assertThat(em.find(Profile.class, perfilId).getProfileId()).isEqualTo("P-001-BIS");
        assertThat(contarFilas("profile")).isEqualTo(1);
    }

    @Test
    @DisplayName("una sola peticion conserva, edita, añade y borra en las dos colecciones")
    void reconciliacionCompleta() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA PRINCIPAL"), viaDto(null, "VIA 3")),
                List.of(estacionDto(estacionId, "ATOCHA"), estacionDto(null, "CHAMARTIN"))));

        assertThat(contarFilas("track")).isEqualTo(2);
        assertThat(em.find(Track.class, segundaViaId)).as("la via 2 no venia: se borra").isNull();
        assertThat(contarFilas("station")).isEqualTo(2);
    }

    @Test
    @DisplayName("quitar una via la borra a ella y en cascada a sus perfiles")
    void borradoEnCascada() {
        aplicar(peticion(
                List.of(viaDto(segundaViaId, "VIA 2")),
                List.of(estacionDto(estacionId, "ATOCHA"))));

        assertThat(em.find(Track.class, primeraViaId)).isNull();
        assertThat(contarFilas("profile")).as("el perfil se va con su via").isZero();
    }

    @Test
    @DisplayName("el hijo nuevo se guarda con la clave ajena a su paquete")
    void hijoNuevoQuedaVinculado() {
        aplicar(peticion(
                List.of(viaDto(primeraViaId, "VIA 1"), viaDto(segundaViaId, "VIA 2"), viaDto(null, "VIA 3")),
                List.of(estacionDto(estacionId, "ATOCHA"))));

        ExecutionPackage releido = em.find(ExecutionPackage.class, paqueteId);

        assertThat(releido.getTracks()).hasSize(3);
        assertThat(releido.getTracks())
                .allSatisfy(via -> assertThat(via.getExecutionPackage().getId()).isEqualTo(paqueteId));
    }
}
