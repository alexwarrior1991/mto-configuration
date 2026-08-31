package com.alejandro.mtoconfiguration.mapper.merge;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Orden de las colecciones de hijos, y por que ya no se puede corromper.
 *
 * <p>{@code Track.profiles} y {@code Profile.cantilevers} usaban {@code @OrderColumn}, que guarda
 * el indice del elemento en una columna de la tabla del hijo. Esa columna la mantiene la <b>lista
 * del padre</b>, no la clave ajena del hijo, asi que un hijo creado por su cuenta —{@code POST
 * /profiles} con un {@code trackId}, que genera {@code profile.setTrack(...)} sin tocar
 * {@code track.getProfiles()}— entraba con la columna a null y la siguiente lectura de la
 * coleccion moria con "Illegal null value for list index": un 500 al abrir la via, provocado por
 * una peticion anterior que no habia fallado.
 *
 * <p>Ahora las dos usan {@code @OrderBy}, que resuelve el orden con un ORDER BY en la consulta.
 * No hay columna que mantener, luego no hay nada que se pueda quedar a medias. Estos tests fijan
 * las dos mitades: que el alta suelta ya no envenena al padre, y que el orden que se obtiene es el
 * que se quiere —los perfiles por punto kilometrico, no por orden de alta—.
 */
class ChildOrderingIT extends AbstractChildMergeIT {

    private Long viaId;

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

        flushAndClear();
        viaId = via.getId();
    }

    private Profile perfilSuelto(String profileId, String kp) {
        // Exactamente lo que produce POST /profiles con un trackId: solo la clave ajena.
        Profile perfil = new Profile();
        perfil.setProfileId(profileId);
        perfil.setKp(new BigDecimal(kp));
        perfil.setTrack(em.getReference(Track.class, viaId));
        em.persist(perfil);
        return perfil;
    }

    @Test
    @DisplayName("un perfil creado suelto ya no rompe la lectura de la via")
    void perfilSueltoNoRompeLaVia() {
        perfilSuelto("P-001", "10.000");
        flushAndClear();

        Track releida = em.find(Track.class, viaId);

        assertThat(releida.getProfiles()).hasSize(1);
        assertThat(releida.getProfiles().getFirst().getProfileId()).isEqualTo("P-001");
    }

    @Test
    @DisplayName("los perfiles se devuelven por punto kilometrico, no por orden de alta")
    void ordenPorPuntoKilometrico() {
        // Se dan de alta a la inversa a proposito: con el orden de insercion saldrian 30, 10, 20.
        perfilSuelto("P-003", "30.000");
        perfilSuelto("P-001", "10.000");
        perfilSuelto("P-002", "20.000");
        flushAndClear();

        Track releida = em.find(Track.class, viaId);

        assertThat(releida.getProfiles())
                .extracting(Profile::getProfileId)
                .containsExactly("P-001", "P-002", "P-003");
    }

    @Test
    @DisplayName("el orden de la coleccion coincide con el de las consultas por keyset")
    void mismoOrdenQueElRestoDelCodigo() {
        // Antes convivian dos ordenes para los mismos datos: la coleccion iba por orden de alta y
        // findByTrackIdOrderByKpAscIdAsc por KP. Ahora es el mismo.
        perfilSuelto("P-003", "30.000");
        perfilSuelto("P-001", "10.000");
        flushAndClear();

        var porConsulta = em.createQuery(
                        "select p from Profile p where p.track.id = :trackId order by p.kp, p.id asc",
                        Profile.class)
                .setParameter("trackId", viaId)
                .getResultList();

        assertThat(em.find(Track.class, viaId).getProfiles())
                .extracting(Profile::getProfileId)
                .containsExactlyElementsOf(porConsulta.stream().map(Profile::getProfileId).toList());
    }

    @Test
    @DisplayName("dos perfiles en el mismo punto kilometrico mantienen un orden estable")
    void desempatePorId() {
        // El KP no es unico: dos perfiles pueden compartirlo. El id desempata para que dos
        // lecturas devuelvan siempre lo mismo, que es lo que necesita la paginacion por keyset.
        Profile primero = perfilSuelto("P-001", "10.000");
        Profile segundo = perfilSuelto("P-002", "10.000");
        flushAndClear();

        assertThat(em.find(Track.class, viaId).getProfiles())
                .extracting(p -> p.getId())
                .containsExactly(primero.getId(), segundo.getId());
    }

    @Test
    @DisplayName("una mensula creada suelta tampoco rompe la lectura del perfil")
    void mensulaSueltaNoRompeElPerfil() {
        Profile perfil = perfilSuelto("P-001", "10.000");
        flushAndClear();

        Cantilever mensula = new Cantilever();
        mensula.setCwHeight(new BigDecimal("5.500"));
        mensula.setStagger(new BigDecimal("200"));
        mensula.setProfile(em.getReference(Profile.class, perfil.getId()));
        em.persist(mensula);
        flushAndClear();

        assertThat(em.find(Profile.class, perfil.getId()).getCantilevers()).hasSize(1);
    }

    @Test
    @DisplayName("las mensulas salen en orden estable por id")
    void mensulasEnOrdenEstable() {
        Profile perfil = perfilSuelto("P-001", "10.000");
        flushAndClear();

        Profile gestionado = em.find(Profile.class, perfil.getId());
        Cantilever primera = new Cantilever();
        primera.setCwHeight(new BigDecimal("5.500"));
        primera.setStagger(new BigDecimal("200"));
        gestionado.addCantilever(primera);
        Cantilever segunda = new Cantilever();
        segunda.setCwHeight(new BigDecimal("6.000"));
        segunda.setStagger(new BigDecimal("210"));
        gestionado.addCantilever(segunda);
        flushAndClear();

        assertThat(em.find(Profile.class, perfil.getId()).getCantilevers())
                .extracting(c -> c.getId())
                .containsExactly(primera.getId(), segunda.getId());
    }

    @Test
    @DisplayName("el mapeo ya no declara ninguna columna de orden")
    void elMapeoNoDeclaraColumnaDeOrden() {
        // En este slice el esquema lo genera Hibernate desde las entidades (Flyway va desactivado),
        // asi que lo que se comprueba es el MAPEO: que ninguna de las cuatro tablas necesita ya la
        // columna. Si alguien reintrodujera @OrderColumn, este test lo dice de inmediato, antes de
        // que el desajuste con la migracion V8 aparezca al arrancar.
        // Que la migracion en si deja el esquema coherente con las entidades lo cubre
        // FlywayMigrationIT, que corre las migraciones reales y despues valida contra el modelo.
        Long columnas = ((Number) em.createNativeQuery("""
                select count(*) from information_schema.columns
                 where table_name in ('profile', 'profile_aud', 'cantilever', 'cantilever_aud')
                   and column_name = 'insertion_order'
                """).getSingleResult()).longValue();

        assertThat(columnas).isZero();
    }
}
