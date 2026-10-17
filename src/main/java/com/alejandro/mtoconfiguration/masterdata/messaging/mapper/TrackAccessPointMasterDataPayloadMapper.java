package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.infrastructure.TrackAccessPoint;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El evento {@code track-access-point} (V28), que materializa {@code mto-resources}.
 *
 * <p>Las claves son contrato: solo se añaden. La vía lleva el id de su paquete de ejecución
 * ({@code executionPackageId}) porque el consumidor guarda el paquete del acceso y no recibe el evento
 * {@code track}; se lee del proxy sin inicializarlo, así que no entra en el grafo de
 * {@code TrackAccessPointRepository.findByIdForMessaging}.
 */
@Component
public class TrackAccessPointMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<TrackAccessPoint> {

    @Override
    public Class<TrackAccessPoint> supportedType() {
        return TrackAccessPoint.class;
    }

    @Override
    public Map<String, Object> toPayload(TrackAccessPoint accessPoint) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", accessPoint.getId());
        values.put("name", accessPoint.getName());
        values.put("enabled", accessPoint.getEnabled());
        values.put("kp", accessPoint.getKp());
        values.put("kind", accessPoint.getKind() == null ? null : accessPoint.getKind().name());
        values.put("side", accessPoint.getSide() == null ? null : accessPoint.getSide().name());
        values.put("roadRailAccess", accessPoint.getRoadRailAccess());
        values.put("maxAxleLoadT", accessPoint.getMaxAxleLoadT());
        values.put("maxGrossWeightT", accessPoint.getMaxGrossWeightT());
        values.put("latitude", accessPoint.getLatitude());
        values.put("longitude", accessPoint.getLongitude());
        values.put("station", toStationPayload(accessPoint.getStation()));
        values.put("track", toTrackPayload(accessPoint.getTrack()));

        return values;
    }

    private Map<String, Object> toStationPayload(Station station) {
        if (station == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", station.getId());
        values.put("name", station.getName());
        return values;
    }

    private Map<String, Object> toTrackPayload(Track track) {
        if (track == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", track.getId());
        values.put("name", track.getName());
        values.put("executionPackageId", track.getExecutionPackage() == null
                ? null
                : track.getExecutionPackage().getId());
        return values;
    }
}
