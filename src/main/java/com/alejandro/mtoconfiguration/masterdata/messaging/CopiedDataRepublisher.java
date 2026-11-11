package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.SectionInsulatorRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Vuelve a publicar los perfiles, los seccionadores y los aisladores de una vía o de una estación
 * cuando cambia en ella <b>algo que su evento copia</b>.
 *
 * <p>El evento {@code profile} lleva de su vía el nombre, si está habilitada, sus estaciones y su
 * paquete ({@code track.executionPackageId}); los de {@code disconnector} y {@code section-insulator},
 * el nombre de su vía, el de la conectada y el de su estación. Pasar una vía a otro paquete no
 * publicaba ninguno, y {@code mto-maintenance}, que guarda el paquete de cada activo (su ámbito por
 * paquete y sus órdenes de un solo paquete salen de ahí), se quedaba con el viejo hasta un
 * republicado a mano (README_MESSAGING.md §2.4).
 *
 * <p>Qué cambio vuelve a publicar a quién:
 *
 * <table>
 *   <caption>Lo que se vuelve a publicar</caption>
 *   <tr><th>Cambia</th><th>Se publica</th></tr>
 *   <tr><td>el nombre de la vía</td><td>sus perfiles, y los seccionadores y aisladores que la
 *       nombran (como vía propia o conectada)</td></tr>
 *   <tr><td>el paquete de la vía</td><td>sus perfiles y, detrás, los seccionadores de sus postes o
 *       con ella como vía propia y los aisladores de esa vía: su evento no lleva el paquete, pero
 *       el consumidor lo deduce de la vía al recibirlos</td></tr>
 *   <tr><td>{@code enabled} o las estaciones de la vía</td><td>sus perfiles</td></tr>
 *   <tr><td>el nombre de la estación</td><td>sus seccionadores y sus aisladores</td></tr>
 * </table>
 *
 * <p>Qué ha cambiado lo dice Hibernate ({@link EntityChangeTracker#changedProperties}): un
 * {@code PUT} que devuelve la vía como estaba, o que solo toca a sus hijos, no vuelve a publicar
 * nada. Lo que esa misma escritura ya publica por sí mismo (un perfil modificado dentro del
 * {@code PUT} de su vía) tampoco sale dos veces ({@link EntityChangeTracker#publishesItsOwnEvent}).
 *
 * <p>Una vía tiene cientos o miles de perfiles: se leen los ids y se cargan por lotes de
 * {@value #BATCH_SIZE} con el grafo de mensajería ({@code findAllByIdForMessaging}), una sentencia
 * por lote y no una por perfil, y cada uno se escribe en el outbox con
 * {@link MasterDataEventPublisher}, como hace el republicado, en la transacción del cambio.
 *
 * <p>Los puntos de acceso tienen su propio {@link TrackAccessPointRepublisher}, que no mira qué
 * cambió. Borrar una vía o una estación no publica nada de lo que se queda colgando.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CopiedDataRepublisher {

    static final int BATCH_SIZE = 200;

    /** Las propiedades de {@code Track} que copia el evento {@code profile}. */
    static final Set<String> TRACK_COPIED_BY_PROFILE = Set.of("name", "enabled", "stations", "executionPackage");
    static final String NAME = "name";
    static final String EXECUTION_PACKAGE = "executionPackage";

    private final ProfileRepository profileRepository;
    private final DisconnectorRepository disconnectorRepository;
    private final SectionInsulatorRepository sectionInsulatorRepository;
    private final MasterDataEventPublisher masterDataEventPublisher;

    @EventListener
    public void onEntityChanged(EntityChangeApplicationEvent<? extends IEntity> event) {
        if (event.operation() != EntityChangeOperation.UPDATED || event.entity() == null
                || event.entity().getId() == null) {
            return;
        }

        Set<String> changed = EntityChangeTracker.changedProperties(event.entity());

        if (changed.isEmpty()) {
            return;
        }

        switch (event.entity()) {
            case Track track -> onTrackChanged(track.getId(), changed);
            case Station station -> onStationChanged(station.getId(), changed);
            default -> {
                // Nadie copia nada de las demas.
            }
        }
    }

    private void onTrackChanged(Long trackId, Set<String> changed) {
        boolean renamed = changed.contains(NAME);
        boolean moved = changed.contains(EXECUTION_PACKAGE);
        int published = 0;

        // Los perfiles primero: el consumidor saca de ellos el paquete de lo demas que hay en la via.
        if (changed.stream().anyMatch(TRACK_COPIED_BY_PROFILE::contains)) {
            published += republish(Profile.class, profileRepository.findIdsByTrackId(trackId),
                    profileRepository::findAllByIdForMessaging);
        }

        Set<Long> disconnectors = new TreeSet<>();
        Set<Long> insulators = new TreeSet<>();

        if (renamed) {
            disconnectors.addAll(disconnectorRepository.findIdsNamingTrack(trackId));
            insulators.addAll(sectionInsulatorRepository.findIdsNamingTrack(trackId));
        }
        if (moved) {
            disconnectors.addAll(disconnectorRepository.findIdsLocatedOnTrack(trackId));
            insulators.addAll(sectionInsulatorRepository.findIdsLocatedOnTrack(trackId));
        }

        published += republish(Disconnector.class, disconnectors, disconnectorRepository::findAllByIdForMessaging);
        published += republish(SectionInsulator.class, insulators,
                sectionInsulatorRepository::findAllByIdForMessaging);

        log.debug("Track {} changed {}: {} dependent events republished", trackId, changed, published);
    }

    private void onStationChanged(Long stationId, Set<String> changed) {
        if (!changed.contains(NAME)) {
            return;
        }

        int published = republish(Disconnector.class, disconnectorRepository.findIdsByStationId(stationId),
                disconnectorRepository::findAllByIdForMessaging);
        published += republish(SectionInsulator.class, sectionInsulatorRepository.findIdsByStationId(stationId),
                sectionInsulatorRepository::findAllByIdForMessaging);

        log.debug("Station {} renamed: {} dependent events republished", stationId, published);
    }

    /**
     * Publica un {@code UPDATED} de cada id que esta escritura no publique ya por su cuenta, cargando
     * por lotes, y lo apunta como publicado para que no salga otra vez (una vía y su estación
     * renombradas en el mismo {@code PUT} de su paquete nombran al mismo seccionador).
     */
    private <E extends IEntity> int republish(Class<E> type, Collection<Long> ids,
                                              Function<Collection<Long>, List<E>> loader) {
        List<Long> pending = new ArrayList<>(ids.size());

        for (Long id : ids) {
            if (!EntityChangeTracker.publishesItsOwnEvent(type, id)) {
                pending.add(id);
            }
        }

        int published = 0;

        for (int from = 0; from < pending.size(); from += BATCH_SIZE) {
            List<Long> batch = pending.subList(from, Math.min(from + BATCH_SIZE, pending.size()));

            for (E entity : loader.apply(batch)) {
                // Sin proxy: el nombre del evento y su mapper de payload salen de la clase.
                masterDataEventPublisher.publish(Hibernate.unproxy(entity), MasterDataOperation.UPDATED);
                EntityChangeTracker.markPublished(entity);
                published++;
            }
        }

        return published;
    }
}
