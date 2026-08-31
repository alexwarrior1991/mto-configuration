package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.TrackMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
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
 * Reconciliacion de los perfiles de una via, contra la base de datos.
 *
 * <p>Lo que se prueba aqui y en ningun otro sitio es la reconciliacion <b>anidada</b>: la via
 * reconcilia sus perfiles y cada perfil reconcilia sus mensulas dentro de la misma peticion. Basta
 * con que un nivel añada en lugar de fusionar para que se duplique el subarbol entero, y ese es el
 * efecto que hacia que el fallo no se quedara en una sola tabla.</p>
 */
class TrackChildMergeIT extends AbstractChildMergeIT {

    @Autowired
    private TrackMapper mapper;

    private Long viaId;
    private Long primerPerfilId;
    private Long segundoPerfilId;
    private Long mensulaId;

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

        Track via = new Track();
        via.setName("VIA 1");
        via.setEnabled(true);
        via.setExecutionPackage(paquete);
        em.persist(via);

        // Por el adder del padre, que mantiene los dos lados de la relacion en memoria.
        Profile primero = new Profile();
        primero.setProfileId("P-001");
        primero.setKp(new BigDecimal("10.000"));
        Cantilever mensula = new Cantilever();
        mensula.setCwHeight(new BigDecimal("5.500"));
        mensula.setStagger(new BigDecimal("200"));
        primero.addCantilever(mensula);
        via.addProfile(primero);
        em.persist(primero);

        Profile segundo = new Profile();
        segundo.setProfileId("P-002");
        segundo.setKp(new BigDecimal("20.000"));
        via.addProfile(segundo);
        em.persist(segundo);

        flushAndClear();

        viaId = via.getId();
        primerPerfilId = primero.getId();
        segundoPerfilId = segundo.getId();
        mensulaId = mensula.getId();
    }

    private static ProfileDTO perfilDto(Long id, String profileId, String kp) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        dto.setProfileId(profileId);
        dto.setKp(kp);
        return dto;
    }

    private static CantileverDTO mensulaDto(Long id, String altura) {
        CantileverDTO dto = new CantileverDTO();
        dto.setId(id);
        dto.setCwHeight(new BigDecimal(altura));
        dto.setStagger(new BigDecimal("200"));
        return dto;
    }

    private TrackDTO peticion(List<ProfileDTO> perfiles) {
        TrackDTO dto = new TrackDTO();
        dto.setId(viaId);
        dto.setName("VIA 1");
        dto.setEnabled(true);
        dto.setProfiles(new ArrayList<>(perfiles));
        return dto;
    }

    private void aplicar(TrackDTO dto) {
        Track gestionada = em.find(Track.class, viaId);
        mapper.updateEntityFromDTO(dto, gestionada);
        flushAndClear();
    }

    @Test
    @DisplayName("editar un perfil actualiza SU fila y no inserta ninguna")
    void editarNoDuplica() {
        aplicar(peticion(List.of(
                perfilDto(primerPerfilId, "P-001-BIS", "11.000"),
                perfilDto(segundoPerfilId, "P-002", "20.000"))));

        assertThat(contarFilas("profile")).isEqualTo(2);
        assertThat(em.find(Profile.class, primerPerfilId).getProfileId()).isEqualTo("P-001-BIS");
    }

    @Test
    @DisplayName("guardar tres veces seguidas deja las mismas filas, tambien las de las mensulas")
    void guardadosRepetidosNoAcumulan() {
        for (String kp : List.of("11.000", "12.000", "13.000")) {
            ProfileDTO primero = perfilDto(primerPerfilId, "P-001", kp);
            primero.setCantilevers(new ArrayList<>(List.of(mensulaDto(mensulaId, "5.500"))));

            aplicar(peticion(List.of(primero, perfilDto(segundoPerfilId, "P-002", "20.000"))));
        }

        assertThat(contarFilas("profile")).isEqualTo(2);
        assertThat(contarFilas("cantilever")).as("el subarbol tampoco crece").isEqualTo(1);
    }

    @Test
    @DisplayName("la reconciliacion llega hasta las mensulas del perfil")
    void reconciliacionAnidada() {
        ProfileDTO primero = perfilDto(primerPerfilId, "P-001", "10.000");
        primero.setCantilevers(new ArrayList<>(List.of(mensulaDto(mensulaId, "9.999"))));

        aplicar(peticion(List.of(primero, perfilDto(segundoPerfilId, "P-002", "20.000"))));

        assertThat(contarFilas("cantilever")).isEqualTo(1);
        assertThat(em.find(Cantilever.class, mensulaId).getCwHeight()).isEqualByComparingTo("9.999");
    }

    @Test
    @DisplayName("quitar un perfil lo borra a el y en cascada a sus mensulas")
    void borradoEnCascada() {
        aplicar(peticion(List.of(perfilDto(segundoPerfilId, "P-002", "20.000"))));

        assertThat(em.find(Profile.class, primerPerfilId)).isNull();
        assertThat(contarFilas("cantilever")).as("la mensula se va con su perfil").isZero();
    }

    @Test
    @DisplayName("un perfil sin id se inserta como fila nueva, con sus mensulas")
    void altaAnidada() {
        ProfileDTO nuevo = perfilDto(null, "P-003", "30.000");
        nuevo.setCantilevers(new ArrayList<>(List.of(mensulaDto(null, "7.777"))));

        aplicar(peticion(List.of(
                perfilDto(primerPerfilId, "P-001", "10.000"),
                perfilDto(segundoPerfilId, "P-002", "20.000"),
                nuevo)));

        assertThat(contarFilas("profile")).isEqualTo(3);
        assertThat(contarFilas("cantilever")).as("la del perfil viejo mas la del nuevo").isEqualTo(2);
    }

    @Test
    @DisplayName("una lista vacia borra todos los perfiles de la via")
    void listaVaciaBorraTodo() {
        aplicar(peticion(List.of()));

        assertThat(contarFilas("profile")).isZero();
        assertThat(contarFilas("cantilever")).isZero();
    }
}
