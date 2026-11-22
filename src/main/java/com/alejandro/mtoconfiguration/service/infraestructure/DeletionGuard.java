package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.EntityInUseException;
import com.alejandro.mtoconfiguration.core.exception.EntityInUseException.Dependent;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.service.commons.event.BeforeDeleteCheck;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lo que impide borrar un maestro: lo que cuelga de el y sigue vivo.
 *
 * <p>El borrado es logico, asi que ninguna clave ajena lo frena: la fila del padre se queda y lo
 * que apuntaba a ella sigue apuntando, ya a un maestro que nadie puede leer. Antes el {@code DELETE}
 * lo permitia y dejaba esas filas huerfanas (el seccionador de una estacion borrada, el perfil de
 * una via borrada). Ahora se cuenta lo que cuelga y, si hay algo, el borrado es un 409
 * {@code BUS-002} que dice que y cuanto ({@link EntityInUseException}), sin escribir nada.
 *
 * <p>Lo que cuelga de cada maestro (solo lo vivo; lo ya borrado no impide nada):
 * <ul>
 *   <li>paquete de ejecucion: sus vias y sus estaciones;</li>
 *   <li>estacion: las vias enlazadas a ella, sus seccionadores, sus aisladores y sus puntos de
 *       acceso;</li>
 *   <li>via: sus perfiles, los seccionadores que estan en sus postes, la maniobran o la conectan,
 *       los aisladores que estan en ella o la conectan, las agujas de otros aisladores que dan a
 *       ella y sus puntos de acceso;</li>
 *   <li>perfil: los seccionadores de su poste.</li>
 * </ul>
 * Lo que <b>no</b> impide nada porque se va con su padre ({@code CRUDEntity.deleteCascade}): las
 * mensulas de un perfil con sus brazos, el brazo de una mensula y las agujas de un aislador.
 *
 * <p><b>La carrera.</b> Contar no basta si alguien da de alta un hijo a la vez. Antes de contar se
 * bloquea la fila del padre con un {@code select ... for update} nativo, que es el unico bloqueo
 * que choca con el {@code for key share} que toma la clave ajena de quien inserta o reapunta un
 * hijo (el {@code PESSIMISTIC_WRITE} de Hibernate es en PostgreSQL {@code for no key update}, que
 * no choca; tampoco el {@code UPDATE} del propio borrado). Asi los dos se ponen en fila: si el alta
 * va delante, este recuento espera a que confirme y la ve (409); si va detras, espera ella, y al
 * seguir encuentra a su padre borrado y la rechaza {@code LiveParentGuard}.
 */
@Component
public class DeletionGuard implements BeforeDeleteCheck {

    private static final String TRACK_HINT =
            " Bórralos antes, o borra la vía entera con todo lo suyo: DELETE /tracks/{id}?cascade=true.";
    private static final String DEFAULT_HINT = " Bórralos o muévelos antes.";

    private record Hanging(String type, String one, String many, String countSql) {
    }

    private record Rule(String table, String name, String hint, List<Hanging> hangings) {
    }

    private static final Hanging TRACK_PROFILES = new Hanging("profiles", "perfil", "perfiles",
            "select count(*) from profile where track_id = :id and deleted = false");

    private static final Rule TRACK = new Rule("track", "la vía", TRACK_HINT, List.of(
            TRACK_PROFILES,
            new Hanging("disconnectors", "seccionador", "seccionadores", """
                    select count(*) from disconnector d
                    where d.deleted = false
                      and (d.track_id = :id or d.connected_track_id = :id
                           or exists (select 1 from profile p
                                      where p.id = d.profile_id and p.track_id = :id and p.deleted = false))
                    """),
            new Hanging("sectionInsulators", "aislador", "aisladores", """
                    select count(*) from section_insulator
                    where deleted = false and (track_id = :id or connected_track_id = :id)
                    """),
            new Hanging("sectionInsulatorSwitches", "aguja de otro aislador", "agujas de otros aisladores", """
                    select count(*) from section_insulator_switch s
                    join section_insulator i on i.id = s.section_insulator_id
                    where s.deleted = false and i.deleted = false and s.track_id = :id
                      and i.track_id is distinct from :id and i.connected_track_id is distinct from :id
                    """),
            new Hanging("trackAccessPoints", "punto de acceso", "puntos de acceso",
                    "select count(*) from track_access_point where track_id = :id and deleted = false")));

    private static final Map<Class<?>, Rule> RULES = Map.of(
            ExecutionPackage.class, new Rule("execution_package", "el paquete de ejecución", DEFAULT_HINT, List.of(
                    new Hanging("tracks", "vía", "vías",
                            "select count(*) from track where execution_package_id = :id and deleted = false"),
                    new Hanging("stations", "estación", "estaciones",
                            "select count(*) from station where execution_package_id = :id and deleted = false"))),
            Station.class, new Rule("station", "la estación", DEFAULT_HINT, List.of(
                    new Hanging("tracks", "vía enlazada", "vías enlazadas", """
                            select count(*) from track_station ts join track t on t.id = ts.track_id
                            where ts.station_id = :id and t.deleted = false
                            """),
                    new Hanging("disconnectors", "seccionador", "seccionadores",
                            "select count(*) from disconnector where station_id = :id and deleted = false"),
                    new Hanging("sectionInsulators", "aislador", "aisladores",
                            "select count(*) from section_insulator where station_id = :id and deleted = false"),
                    new Hanging("trackAccessPoints", "punto de acceso", "puntos de acceso",
                            "select count(*) from track_access_point where station_id = :id and deleted = false"))),
            Track.class, TRACK,
            Profile.class, new Rule("profile", "el perfil", DEFAULT_HINT, List.of(
                    new Hanging("disconnectors", "seccionador", "seccionadores",
                            "select count(*) from disconnector where profile_id = :id and deleted = false"))));

    /**
     * Lo de <b>otra</b> via que apunta a esta, que es lo unico que impide borrarla entera: el
     * seccionador que la maniobra desde el poste de otra o que la conecta sin estar en ella, el
     * aislador de otra via que la tiene de via conectada y la aguja de un aislador de otra via que
     * da a ella. Un seccionador «esta» en la via de su poste o, sin poste, en su via propia; un
     * aislador, en su {@code track_id}.
     */
    private static final List<Hanging> FROM_OTHER_TRACKS = List.of(
            new Hanging("disconnectors", "seccionador", "seccionadores", """
                    select count(*) from disconnector d
                    where d.deleted = false
                      and (d.track_id = :id or d.connected_track_id = :id)
                      and not (case when d.profile_id is null then d.track_id is not distinct from :id
                                    else exists (select 1 from profile p
                                                 where p.id = d.profile_id and p.track_id = :id) end)
                    """),
            new Hanging("sectionInsulators", "aislador", "aisladores", """
                    select count(*) from section_insulator
                    where deleted = false and connected_track_id = :id and track_id is distinct from :id
                    """),
            new Hanging("sectionInsulatorSwitches", "aguja", "agujas", """
                    select count(*) from section_insulator_switch s
                    join section_insulator i on i.id = s.section_insulator_id
                    where s.deleted = false and i.deleted = false and s.track_id = :id
                      and i.track_id is distinct from :id
                    """));

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Bloquea la fila de la entidad y comprueba que no cuelga nada vivo de ella; si cuelga, 409. Va
     * antes de tocar nada. Una entidad de la que no cuelga nada ajeno (seccionador, aislador,
     * mensula, brazo, punto de acceso) solo se bloquea, para que el hijo propio que alguien le este
     * dando de alta a la vez (la aguja, el brazo) espere y se encuentre con su padre borrado.
     */
    @Override
    public void beforeDelete(CRUDEntity entity) {
        Class<?> type = Hibernate.getClass(entity);
        Rule rule = RULES.get(type);

        if (rule == null) {
            lock(tableOf(entity), entity.getId());
            return;
        }

        lock(rule.table(), entity.getId());
        List<Dependent> hanging = count(rule.hangings(), entity.getId());

        if (!hanging.isEmpty()) {
            throw new EntityInUseException("No se puede borrar " + rule.name() + ": tiene "
                    + describe(rule.hangings(), hanging) + "." + rule.hint(), hanging);
        }
    }

    /**
     * El principio del borrado en cascada de una via: bloquea la via y todo lo suyo de lo que
     * cuelgan hijos (sus perfiles, sus mensulas y sus aisladores), en ese orden, para que el hijo
     * que alguien este dando de alta a la vez o ya este confirmado cuando se cargue la rama, o
     * espere y sea rechazado; y comprueba que nada de otra via apunta a ella. Si algo apunta, 409:
     * la cascada no borra ni cambia nada que no sea de la via.
     */
    public void requireTrackCanGoWhole(Long trackId) {
        lock("track", trackId);
        entityManager.createNativeQuery(
                        "select id from profile where track_id = :id and deleted = false order by id for update")
                .setParameter("id", trackId).getResultList();
        entityManager.createNativeQuery("""
                        select c.id from cantilever c join profile p on p.id = c.profile_id
                        where p.track_id = :id and p.deleted = false and c.deleted = false
                        order by c.id for update of c
                        """)
                .setParameter("id", trackId).getResultList();
        entityManager.createNativeQuery(
                        "select id from section_insulator where track_id = :id and deleted = false order by id for update")
                .setParameter("id", trackId).getResultList();

        List<Dependent> foreign = count(FROM_OTHER_TRACKS, trackId);

        if (!foreign.isEmpty()) {
            throw new EntityInUseException("No se puede borrar la vía entera: desde otras vías la usan "
                    + describe(FROM_OTHER_TRACKS, foreign)
                    + ". Cambia antes esas referencias o bórralos: lo que no es de la vía no se toca.", foreign);
        }
    }

    /**
     * El final de la cascada, con todo ya escrito: no puede quedar nada vivo que apunte a la via.
     * Si queda (una relacion nueva que la cascada no conoce), 409 y la transaccion se deshace.
     */
    public void requireNothingLeftOnTrack(Long trackId) {
        List<Dependent> left = count(TRACK.hangings(), trackId);

        if (!left.isEmpty()) {
            throw new EntityInUseException("No se puede borrar la vía entera: quedan "
                    + describe(TRACK.hangings(), left) + " que apuntan a ella.", left);
        }
    }

    private void lock(String table, Long id) {
        if (table != null && id != null) {
            entityManager.createNativeQuery("select id from " + table + " where id = :id for update")
                    .setParameter("id", id)
                    .getResultList();
        }
    }

    private List<Dependent> count(List<Hanging> hangings, Long id) {
        List<Dependent> found = new ArrayList<>();

        for (Hanging hanging : hangings) {
            long count = ((Number) entityManager.createNativeQuery(hanging.countSql())
                    .setParameter("id", id)
                    .getSingleResult()).longValue();

            if (count > 0) {
                found.add(new Dependent(hanging.type(), count));
            }
        }

        return found;
    }

    /** «412 perfiles, 3 seccionadores y 1 aislador», en el orden de la regla. */
    private static String describe(List<Hanging> hangings, List<Dependent> found) {
        List<String> parts = new ArrayList<>();

        for (Dependent dependent : found) {
            Hanging hanging = hangings.stream()
                    .filter(candidate -> candidate.type().equals(dependent.type()))
                    .findFirst()
                    .orElseThrow();
            parts.add(dependent.count() + " " + (dependent.count() == 1 ? hanging.one() : hanging.many()));
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        return String.join(", ", parts.subList(0, parts.size() - 1)) + " y " + parts.get(parts.size() - 1);
    }

    /** La tabla de una entidad sin regla, leida de su {@code @Table}; sin ella no se bloquea. */
    private static String tableOf(CRUDEntity entity) {
        Table table = Hibernate.getClass(entity).getAnnotation(Table.class);

        return table == null ? null : table.name().toLowerCase(Locale.ROOT);
    }
}
