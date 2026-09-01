package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackCriteriaSearchRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Borrado logico: que una fila marcada como borrada desaparezca de verdad.
 *
 * <p>Todo el modelo cuelga de {@code CRUDEntity}, que lleva {@code @SQLRestriction("deleted =
 * false")}. Esa anotacion es lo unico que impide que una fila borrada siga apareciendo, y hasta
 * ahora solo estaba probado el lado de dentro: que {@code CRUDService} llama a
 * {@code entity.delete()}. Eso demuestra que se pone la marca, no que la marca <b>sirva</b>.
 *
 * <p>Aqui se comprueba lo otro, que es lo que ve el usuario: que la fila se cae de {@code findAll},
 * de la busqueda por criteria y de la coleccion del padre, y que sin embargo <b>sigue estando</b>
 * en la tabla. Esa ultima parte importa igual: si el borrado fuese fisico se perderia el historico
 * de auditoria y se romperia cualquier referencia que apuntase a la fila.
 */
class SoftDeleteIT extends AbstractCriteriaSearchIT {

    @Autowired
    private TrackRepository repository;

    @Autowired
    private TrackCriteriaSearchRepository criteriaSearchRepository;

    private Long viaBorradaId;
    private Long viaVivaId;
    private Long paqueteId;

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

        Track viva = via("VIA VIVA", paquete);
        Track borrada = via("VIA BORRADA", paquete);

        flushAndClear();

        paqueteId = paquete.getId();
        viaVivaId = viva.getId();
        viaBorradaId = borrada.getId();
    }

    private Track via(String nombre, ExecutionPackage paquete) {
        Track via = new Track();
        via.setName(nombre);
        via.setEnabled(true);
        paquete.addTrack(via);
        em.persist(via);
        return via;
    }

    /** Marca la via como borrada por el mismo camino que usa CRUDService.softDelete. */
    private void borrar(Long id) {
        Track via = em.find(Track.class, id);
        via.delete();
        em.merge(via);
        flushAndClear();
    }

    private long contarFilasReales(String tabla) {
        return ((Number) em.createNativeQuery("select count(*) from " + tabla).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("una via borrada desaparece de findAll")
    void desapareceDeFindAll() {
        borrar(viaBorradaId);

        assertThat(repository.findAll())
                .extracting(Track::getName)
                .containsExactly("VIA VIVA");
    }

    @Test
    @DisplayName("una via borrada desaparece de findById")
    void desapareceDeFindById() {
        borrar(viaBorradaId);

        assertThat(repository.findById(viaBorradaId)).isEmpty();
        assertThat(repository.findById(viaVivaId)).isPresent();
    }

    @Test
    @DisplayName("una via borrada desaparece de la busqueda por criteria, tambien del total")
    void desapareceDeLaBusqueda() {
        borrar(viaBorradaId);

        var resultado = criteriaSearchRepository.criteriaSearchWithChildren(
                Track.class, search(Map.of()), em, Map.of());

        assertThat(resultado.getContent()).extracting(Track::getName).containsExactly("VIA VIVA");
        assertThat(resultado.getTotalElements())
                .as("el total tambien tiene que descontarla")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("una via borrada desaparece de la coleccion de su paquete")
    void desapareceDeLaColeccionDelPadre() {
        // Es el caso que se ve en la API: GET /execution-packages/{id} no debe devolverla.
        borrar(viaBorradaId);

        assertThat(em.find(ExecutionPackage.class, paqueteId).getTracks())
                .extracting(Track::getName)
                .containsExactly("VIA VIVA");
    }

    @Test
    @DisplayName("pero la fila sigue en la tabla: el borrado es logico, no fisico")
    void laFilaSigueEnLaTabla() {
        // Si fuese fisico se perderia el historico de Envers y se romperia cualquier referencia
        // que apuntase a la fila.
        borrar(viaBorradaId);

        assertThat(contarFilasReales("track")).isEqualTo(2);
        assertThat(em.createNativeQuery(
                        "select deleted from track where id = " + viaBorradaId).getSingleResult())
                .isEqualTo(true);
    }

    @Test
    @DisplayName("el filtro se aplica tambien a los hijos, no solo al padre")
    void seAplicaALosHijos() {
        Track via = em.find(Track.class, viaVivaId);
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        via.addProfile(perfil);
        em.persist(perfil);
        flushAndClear();

        Profile persistido = em.find(Profile.class, perfil.getId());
        persistido.delete();
        em.merge(persistido);
        flushAndClear();

        assertThat(em.find(Track.class, viaVivaId).getProfiles())
                .as("el perfil borrado no cuelga ya de su via")
                .isEmpty();
        assertThat(contarFilasReales("profile")).isEqualTo(1);
    }

    @Test
    @DisplayName("borrar el padre no borra a los hijos: hay que hacerlo explicitamente")
    void borrarElPadreNoBorraLosHijos() {
        // Queda escrito porque es una asimetria facil de suponer al reves: orphanRemoval borra el
        // hijo cuando se saca de la coleccion, pero marcar el padre como borrado no propaga nada.
        Track via = em.find(Track.class, viaVivaId);
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        via.addProfile(perfil);
        em.persist(perfil);
        flushAndClear();

        borrar(viaVivaId);

        assertThat(em.createNativeQuery(
                        "select deleted from profile where id = " + perfil.getId()).getSingleResult())
                .as("el perfil sigue sin marcar")
                .isEqualTo(false);
    }

    @Test
    @DisplayName("restore vuelve a hacer visible la fila")
    void restoreLaDevuelve() {
        borrar(viaBorradaId);
        assertThat(repository.findById(viaBorradaId)).isEmpty();

        // No hay endpoint para esto, pero la entidad lo permite y conviene saber que funciona.
        em.createNativeQuery("update track set deleted = false where id = " + viaBorradaId)
                .executeUpdate();
        flushAndClear();

        assertThat(repository.findById(viaBorradaId)).isPresent();
    }
}
