package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackAccessPointRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Vuelve a publicar los puntos de acceso de una vía o de una estación que se modifica.
 *
 * <p>El evento {@code track-access-point} copia el nombre de su vía, el paquete de esa vía y el nombre
 * de su estación, y {@code mto-resources}, que lo consume, no escucha los eventos {@code track} ni
 * {@code station}. Sin esto, renombrar una vía o pasarla a otro paquete no le llegaba hasta que alguien
 * tocase cada acceso o lanzase el republicado (README_MESSAGING.md §2.4). Cada acceso sale como un
 * {@code UPDATED} propio, en la misma transacción que el cambio de su vía: este listener publica el
 * mismo evento de aplicación que una escritura del servicio, y {@link MasterDataEntityChangedEventListener}
 * lo lleva al outbox.
 *
 * <p>Borrar una vía o una estación no toca sus accesos, como no toca nada de lo que cuelga de un
 * maestro: el acceso sigue vivo con la vía de antes, y se borra o se desactiva aparte.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TrackAccessPointRepublisher {

    private final TrackAccessPointRepository trackAccessPointRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @EventListener
    public void onEntityChanged(EntityChangeApplicationEvent<? extends IEntity> event) {
        if (event.operation() != EntityChangeOperation.UPDATED || event.entity() == null
                || event.entity().getId() == null) {
            return;
        }
        List<TrackAccessPoint> accessPoints = switch (event.entity()) {
            case Track track -> trackAccessPointRepository.findByTrackIdOrderByKpAscIdAsc(track.getId());
            case Station station -> trackAccessPointRepository.findByStationIdOrderByKpAscIdAsc(station.getId());
            default -> List.of();
        };
        accessPoints.forEach(accessPoint -> applicationEventPublisher.publishEvent(
                new EntityChangeApplicationEvent<>(accessPoint, EntityChangeOperation.UPDATED)));
    }
}
