package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import com.alejandro.mtoconfiguration.service.commons.event.WrittenEntitiesCheck;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * La otra mitad de {@link DeletionGuard}: lo que se acaba de dar de alta, o de apuntar a otro
 * padre, no puede haberse quedado colgando de un maestro que otra peticion borro mientras tanto.
 *
 * <p>El mapper ya rechaza la referencia a un maestro borrado ({@code BaseMapper.requireNotGone}),
 * pero mira lo que habia al leer. Si el borrado del padre se confirma entre esa lectura y el
 * {@code INSERT} del hijo (que espera a que termine, porque su clave ajena choca con el bloqueo
 * del que borra), el hijo entraria apuntando a un padre borrado, y el que borro, que conto antes,
 * no lo vio. Por eso se vuelve a mirar aqui, <b>despues del flush</b> y con una consulta nueva, que
 * ya ve lo que el otro confirmo: cada padre de cada fila creada, o modificada en una de sus
 * referencias, sigue vivo. Si no, 400 {@code BUS-001} en su campo, como en el mapper, y la
 * transaccion se deshace.
 *
 * <p>El padre que borra <b>esta misma</b> escritura no cuenta (el {@code PUT} de un paquete que
 * quita una via y trae, dentro de una estacion, un seccionador que la nombra): eso se deja pasar,
 * como en {@code requireNotGone}.
 */
@Component
public class LiveParentGuard implements WrittenEntitiesCheck {

    private record Reference(String table, Long id, String property, String field) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void afterFlush(List<EntityChangeTracker.Change> written) {
        Map<String, List<Reference>> byTable = new LinkedHashMap<>();
        Map<String, Set<Long>> deletedHere = new LinkedHashMap<>();

        for (EntityChangeTracker.Change change : written) {
            Object entity = Hibernate.unproxy(change.entity());

            if (change.operation() == EntityChangeOperation.DELETED) {
                String table = tableOf(entity);

                if (table != null) {
                    deletedHere.computeIfAbsent(table, key -> new HashSet<>()).add(change.entity().getId());
                }

                continue;
            }

            Set<String> changed = change.operation() == EntityChangeOperation.CREATED
                    ? null
                    : EntityChangeTracker.changedProperties(change.entity());

            for (Reference reference : referencesOf(entity)) {
                if (changed == null || changed.contains(reference.property())) {
                    byTable.computeIfAbsent(reference.table(), key -> new ArrayList<>()).add(reference);
                }
            }
        }

        byTable.forEach((table, references) -> check(table, references, deletedHere.getOrDefault(table, Set.of())));
    }

    private void check(String table, List<Reference> references, Set<Long> deletedHere) {
        Set<Long> ids = new LinkedHashSet<>();
        references.forEach(reference -> ids.add(reference.id()));
        ids.removeAll(deletedHere);

        if (ids.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Number> gone = entityManager
                .createNativeQuery("select id from " + table + " where id in (:ids) and deleted = true")
                .setParameter("ids", ids)
                .getResultList();

        if (!gone.isEmpty()) {
            long goneId = gone.get(0).longValue();
            String field = references.stream()
                    .filter(reference -> reference.id() == goneId)
                    .map(Reference::field)
                    .findFirst()
                    .orElseThrow();

            throw new ValidationException(List.of(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, field)));
        }
    }

    private static List<Reference> referencesOf(Object entity) {
        List<Reference> references = new ArrayList<>();

        switch (entity) {
            case Track track -> {
                add(references, "execution_package", track.getExecutionPackage(), "executionPackage", "executionPackageId");
                addAll(references, "station", track.getStations(), "stations", "stations");
            }
            case Station station ->
                    add(references, "execution_package", station.getExecutionPackage(), "executionPackage", "executionPackageId");
            case Profile profile -> add(references, "track", profile.getTrack(), "track", "trackId");
            case Cantilever cantilever -> add(references, "profile", cantilever.getProfile(), "profile", "profileId");
            case SteadyArm arm -> add(references, "cantilever", arm.getCantilever(), "cantilever", "cantileverId");
            case Disconnector disconnector -> {
                add(references, "station", disconnector.getStation(), "station", "stationId");
                add(references, "profile", disconnector.getProfile(), "profile", "profileId");
                add(references, "track", disconnector.getTrack(), "track", "trackId");
                add(references, "track", disconnector.getConnectedTrack(), "connectedTrack", "connectedTrackId");
            }
            case SectionInsulator insulator -> {
                add(references, "station", insulator.getStation(), "station", "stationId");
                add(references, "track", insulator.getTrack(), "track", "trackId");
                add(references, "track", insulator.getConnectedTrack(), "connectedTrack", "connectedTrackId");
            }
            case SectionInsulatorSwitch insulatorSwitch -> {
                add(references, "section_insulator", insulatorSwitch.getSectionInsulator(), "sectionInsulator", "sectionInsulatorId");
                add(references, "track", insulatorSwitch.getTrack(), "track", "trackId");
            }
            case TrackAccessPoint accessPoint -> {
                add(references, "track", accessPoint.getTrack(), "track", "trackId");
                add(references, "station", accessPoint.getStation(), "station", "stationId");
            }
            default -> {
                // Paquetes y catalogos: no cuelgan de ningun maestro con borrado logico.
            }
        }

        return references;
    }

    private static void add(List<Reference> references, String table, IEntity parent, String property, String field) {
        // El id de un proxy se lee sin inicializarlo.
        if (parent != null && parent.getId() != null) {
            references.add(new Reference(table, parent.getId(), property, field));
        }
    }

    private static void addAll(List<Reference> references, String table, Collection<? extends IEntity> parents,
                               String property, String field) {
        if (parents != null && Hibernate.isInitialized(parents)) {
            parents.forEach(parent -> add(references, table, parent, property, field));
        }
    }

    private static String tableOf(Object entity) {
        if (!(entity instanceof CRUDEntity)) {
            return null;
        }

        Table table = entity.getClass().getAnnotation(Table.class);

        return table == null ? null : table.name().toLowerCase(Locale.ROOT);
    }
}
