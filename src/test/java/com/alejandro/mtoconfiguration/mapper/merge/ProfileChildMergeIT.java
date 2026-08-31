package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.mapper.infraestructure.ProfileMapper;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
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
 * Reconciliacion de las mensulas de un perfil, contra la base de datos.
 *
 * <p>Es el caso donde el fallo original salia mas caro, asi que ademas de comprobar que no se
 * duplican filas se comprueba el limite de tres mensulas por perfil y que la coleccion sigue
 * saliendo ordenada y sin huecos tras sacar una del medio.</p>
 */
class ProfileChildMergeIT extends AbstractChildMergeIT {

    @Autowired
    private ProfileMapper mapper;

    private Long perfilId;
    private Long primeraMensulaId;
    private Long segundaMensulaId;
    private Long terceraMensulaId;

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

        // Por los adders, que mantienen los dos lados de la relacion en memoria. Ya no es
        // obligatorio —las colecciones se ordenan con @OrderBy y no hay columna que rellenar—,
        // pero deja el grafo coherente sin depender de una relectura.
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        perfil.addCantilever(mensula("5.500"));
        perfil.addCantilever(mensula("6.000"));
        perfil.addCantilever(mensula("6.500"));
        via.addProfile(perfil);
        em.persist(perfil);

        flushAndClear();

        Profile persistido = em.find(Profile.class, buscarPerfilId());
        perfilId = persistido.getId();
        primeraMensulaId = persistido.getCantilevers().get(0).getId();
        segundaMensulaId = persistido.getCantilevers().get(1).getId();
        terceraMensulaId = persistido.getCantilevers().get(2).getId();

        flushAndClear();
    }

    private Long buscarPerfilId() {
        return ((Number) em.createNativeQuery("select id from profile limit 1").getSingleResult()).longValue();
    }

    private static Cantilever mensula(String altura) {
        Cantilever mensula = new Cantilever();
        mensula.setCwHeight(new BigDecimal(altura));
        mensula.setStagger(new BigDecimal("200"));
        return mensula;
    }

    private static CantileverDTO mensulaDto(Long id, String altura) {
        CantileverDTO dto = new CantileverDTO();
        dto.setId(id);
        dto.setCwHeight(new BigDecimal(altura));
        dto.setStagger(new BigDecimal("200"));
        return dto;
    }

    private ProfileDTO peticion(List<CantileverDTO> mensulas) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(perfilId);
        dto.setProfileId("P-001");
        dto.setKp("10.000");
        dto.setCantilevers(new ArrayList<>(mensulas));
        return dto;
    }

    /** Repite el camino del servicio: cargar el padre gestionado, mapear encima y escribir. */
    private void aplicar(ProfileDTO dto) {
        Profile gestionado = em.find(Profile.class, perfilId);
        mapper.updateEntityFromDTO(dto, gestionado);
        flushAndClear();
    }

    @Test
    @DisplayName("editar una mensula actualiza SU fila y no inserta ninguna")
    void editarNoDuplica() {
        aplicar(peticion(List.of(
                mensulaDto(primeraMensulaId, "9.999"),
                mensulaDto(segundaMensulaId, "6.000"),
                mensulaDto(terceraMensulaId, "6.500"))));

        assertThat(contarFilas("cantilever")).isEqualTo(3);
        assertThat(em.find(Cantilever.class, primeraMensulaId).getCwHeight()).isEqualByComparingTo("9.999");
    }

    @Test
    @DisplayName("guardar tres veces seguidas deja las mismas tres filas")
    void guardadosRepetidosNoAcumulan() {
        // Antes del arreglo esto era 3 -> 6 -> 12 -> 24.
        for (String altura : List.of("6.100", "6.200", "6.300")) {
            aplicar(peticion(List.of(
                    mensulaDto(primeraMensulaId, altura),
                    mensulaDto(segundaMensulaId, "6.000"),
                    mensulaDto(terceraMensulaId, "6.500"))));
        }

        assertThat(contarFilas("cantilever")).isEqualTo(3);
        assertThat(em.find(Cantilever.class, primeraMensulaId).getCwHeight()).isEqualByComparingTo("6.300");
    }

    @Test
    @DisplayName("quitar una mensula de la peticion la BORRA de la tabla")
    void quitarBorraLaFila() {
        aplicar(peticion(List.of(
                mensulaDto(primeraMensulaId, "5.500"),
                mensulaDto(segundaMensulaId, "6.000"))));

        assertThat(contarFilas("cantilever")).isEqualTo(2);
        assertThat(em.find(Cantilever.class, terceraMensulaId)).isNull();
    }

    @Test
    @DisplayName("una mensula sin id se inserta como fila nueva")
    void anadirInsertaUnaSola() {
        // Se sustituye la tercera por una nueva en lugar de sumar una cuarta: el perfil admite
        // como maximo tres (@Size sobre Profile.cantilevers) y pasarse no probaria el alta,
        // fallaria antes en la validacion de la entidad.
        aplicar(peticion(List.of(
                mensulaDto(primeraMensulaId, "5.500"),
                mensulaDto(segundaMensulaId, "6.000"),
                mensulaDto(null, "7.777"))));

        assertThat(contarFilas("cantilever")).isEqualTo(3);
        assertThat(em.find(Cantilever.class, terceraMensulaId)).isNull();
    }

    @Test
    @DisplayName("pasar de tres mensulas lo rechaza la validacion de la entidad")
    void masDeTresMensulasSeRechaza() {
        // El limite lo impone @Size sobre Profile.cantilevers y salta en el flush, no antes.
        Profile gestionado = em.find(Profile.class, perfilId);
        mapper.updateEntityFromDTO(peticion(List.of(
                mensulaDto(primeraMensulaId, "5.500"),
                mensulaDto(segundaMensulaId, "6.000"),
                mensulaDto(terceraMensulaId, "6.500"),
                mensulaDto(null, "7.777"))), gestionado);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> em.flush())
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    @DisplayName("una sola peticion conserva, edita, añade y borra")
    void reconciliacionCompleta() {
        aplicar(peticion(List.of(
                mensulaDto(primeraMensulaId, "9.999"),   // editada
                mensulaDto(segundaMensulaId, "6.000"),   // sin tocar
                mensulaDto(null, "7.777"))));            // nueva; la tercera no viene

        assertThat(contarFilas("cantilever")).isEqualTo(3);
        assertThat(em.find(Cantilever.class, primeraMensulaId).getCwHeight()).isEqualByComparingTo("9.999");
        assertThat(em.find(Cantilever.class, segundaMensulaId).getCwHeight()).isEqualByComparingTo("6.000");
        assertThat(em.find(Cantilever.class, terceraMensulaId)).isNull();
    }

    @Test
    @DisplayName("quitar la mensula del medio deja el resto en orden y sin huecos")
    void ordenSinHuecos() {
        // Profile.cantilevers se ordena con @OrderBy("id ASC"): el orden sale del ORDER BY de la
        // consulta, asi que quitar una del medio no puede dejar un hueco. Con el @OrderColumn que
        // habia antes, si el indice no se recalculaba aparecia un null en su lugar.
        aplicar(peticion(List.of(
                mensulaDto(primeraMensulaId, "5.500"),
                mensulaDto(terceraMensulaId, "6.500"))));

        Profile releido = em.find(Profile.class, perfilId);

        assertThat(releido.getCantilevers()).doesNotContainNull();
        assertThat(releido.getCantilevers())
                .extracting(c -> c.getId())
                .containsExactly(primeraMensulaId, terceraMensulaId);
    }

    @Test
    @DisplayName("una lista vacia borra todas las mensulas del perfil")
    void listaVaciaBorraTodo() {
        aplicar(peticion(List.of()));

        assertThat(contarFilas("cantilever")).isZero();
    }

    @Test
    @DisplayName("la mensula editada conserva su clave ajena al perfil")
    void conservaLaClaveAjena() {
        aplicar(peticion(List.of(mensulaDto(primeraMensulaId, "9.999"))));

        assertThat(em.find(Cantilever.class, primeraMensulaId).getProfile().getId()).isEqualTo(perfilId);
    }
}
