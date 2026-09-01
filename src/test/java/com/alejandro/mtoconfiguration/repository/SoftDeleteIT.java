package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
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
 *
 * <p>La parte de las colecciones no era teorica: la restriccion de clase de {@code CRUDEntity} no
 * se aplica al cargar un {@code @OneToMany}, asi que una via borrada seguia devolviendose dentro
 * de su paquete. Cada asociacion repite ahora la restriccion, y estos tests son lo que impide que
 * se olvide en la siguiente que se añada.
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
    @DisplayName("un aislador borrado desaparece de la coleccion de su estacion")
    void desapareceDeLaColeccionDeStation() {
        Station estacion = new Station();
        estacion.setName("ATOCHA");
        estacion.setExecutionPackage(em.find(ExecutionPackage.class, paqueteId));
        em.persist(estacion);

        SectionInsulator aislador = new SectionInsulator();
        aislador.setName("AISL-1");
        aislador.setEnabled(true);
        estacion.addSectionInsulator(aislador);
        em.persist(aislador);
        flushAndClear();

        SectionInsulator persistido = em.find(SectionInsulator.class, aislador.getId());
        persistido.delete();
        em.merge(persistido);
        flushAndClear();

        assertThat(em.find(Station.class, estacion.getId()).getSectionInsulators()).isEmpty();
    }

    @Test
    @DisplayName("una mensula borrada desaparece de la coleccion de su perfil")
    void desapareceDeLaColeccionDeProfile() {
        Track via = em.find(Track.class, viaVivaId);
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        via.addProfile(perfil);
        em.persist(perfil);

        Cantilever mensula = new Cantilever();
        mensula.setCwHeight(new BigDecimal("5.500"));
        mensula.setStagger(new BigDecimal("200"));
        perfil.addCantilever(mensula);
        flushAndClear();

        Cantilever persistida = em.find(Cantilever.class, mensula.getId());
        persistida.delete();
        em.merge(persistida);
        flushAndClear();

        assertThat(em.find(Profile.class, perfil.getId()).getCantilevers()).isEmpty();
    }

    @Test
    @DisplayName("un seccionador borrado desaparece de la relacion uno a uno de su perfil")
    void desapareceDeLaRelacionUnoAUno() {
        // La restriccion tambien hace falta en el lado inverso de un @OneToOne: si no, el perfil
        // sigue devolviendo un seccionador que ya no existe para el resto de la aplicacion.
        Track via = em.find(Track.class, viaVivaId);
        Profile perfil = new Profile();
        perfil.setProfileId("P-001");
        perfil.setKp(new BigDecimal("10.000"));
        via.addProfile(perfil);
        em.persist(perfil);

        Disconnector seccionador = new Disconnector();
        seccionador.setName("SECC-1");
        seccionador.setOnLoad(true);
        seccionador.setProfile(perfil);
        perfil.setDisconnector(seccionador);
        em.persist(seccionador);
        flushAndClear();

        Disconnector persistido = em.find(Disconnector.class, seccionador.getId());
        persistido.delete();
        em.merge(persistido);
        flushAndClear();

        assertThat(em.find(Profile.class, perfil.getId()).getDisconnector()).isNull();
    }

    @Test
    @DisplayName("un hijo borrado no lo ve la reconciliacion, asi que no se borra fisicamente")
    void elHijoBorradoNoSeBorraFisicamenteAlModificarElPadre() {
        // La consecuencia menos evidente de que la coleccion arrastrase hijos borrados: la
        // reconciliacion de los mappers retira lo que el cliente no manda, y el cliente no manda
        // lo que no ve. Con el hijo borrado dentro de la coleccion, orphanRemoval lo habria
        // borrado FISICAMENTE en la siguiente modificacion del padre, convirtiendo un borrado
        // logico en uno definitivo.
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

        // Modificacion del padre: la coleccion se relee y se vuelve a volcar.
        Track releida = em.find(Track.class, viaVivaId);
        releida.setName("VIA RENOMBRADA");
        em.merge(releida);
        flushAndClear();

        assertThat(contarFilasReales("profile"))
                .as("el perfil borrado sigue en la tabla, solo marcado")
                .isEqualTo(1);
        assertThat(em.createNativeQuery(
                        "select deleted from profile where id = " + perfil.getId()).getSingleResult())
                .isEqualTo(true);
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
