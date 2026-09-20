package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.*;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class StationMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<Station> {

    @Override
    public Class<Station> supportedType() {
        return Station.class;
    }

    @Override
    public Map<String, Object> toPayload(Station station) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", station.getId());
        values.put("name", station.getName());
        values.put("executionPackage", toExecutionPackagePayload(station.getExecutionPackage()));
        values.put("tracks", toTrackPayload(station.getTracks()));
        values.put("disconnectors", toDisconnectorPayload(station.getDisconnectors()));
        values.put("sectionInsulators", toSectionInsulatorPayload(station.getSectionInsulators()));

        return values;
    }

    private Map<String, Object> toExecutionPackagePayload(ExecutionPackage executionPackage) {
        if (executionPackage == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", executionPackage.getId());
        values.put("name", executionPackage.getName());
        values.put("initialPackage", executionPackage.getInitialPackage());
        values.put("enabled", executionPackage.isEnabled());
        return values;
    }

    private List<Map<String, Object>> toTrackPayload(Set<Track> tracks){
        if (tracks == null) {
            return List.of();
        }

        return tracks.stream()
                .sorted(Comparator
                        .comparing((Track track) -> track.getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Track::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTrackPayload)
                .toList();
    }

    private Map<String, Object> toTrackPayload(Track track) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", track.getId());
        values.put("name", track.getName());
        values.put("enabled", track.getEnabled());
        return values;
    }

    private List<Map<String, Object>> toDisconnectorPayload(Set<Disconnector> disconnectors) {
        if (disconnectors == null) {
            return List.of();
        }

        return disconnectors.stream()
                .sorted(Comparator
                        .comparing((Disconnector disconnector) -> disconnector.getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Disconnector::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDisconnectorPayload)
                .toList();
    }

    private Map<String, Object> toDisconnectorPayload(Disconnector disconnector) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", disconnector.getId());
        values.put("name", disconnector.getName());
        values.put("onLoad", disconnector.getOnLoad());
        values.put("profileId", disconnector.getProfile() != null ? disconnector.getProfile().getId() : null);
        values.put("disconnectorFunctionId", disconnector.getDisconnectorFunction() != null ? disconnector.getDisconnectorFunction().getId() : null);
        return values;
    }


    private List<Map<String, Object>> toSectionInsulatorPayload(Set<SectionInsulator> sectionInsulators) {
        if (sectionInsulators == null) {
            return List.of();
        }

        return sectionInsulators.stream()
                .sorted(Comparator
                        .comparing((SectionInsulator sectionInsulator) -> sectionInsulator.getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SectionInsulator::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toSectionInsulatorPayload)
                .toList();
    }

    /**
     * Copia reducida del aislador, con sus dos escalares nuevos pero <b>sin</b> las agujas.
     *
     * <p>{@code kp} e {@code installationType} son columnas de la propia fila, ya cargada, así que
     * no cuestan una sentencia más. Las agujas sí: meterlas aquí obligaría a llevar la colección en
     * el {@code @EntityGraph} de {@code StationRepository.findByIdForMessaging} y multiplicaría las
     * filas del evento de estación por cada aguja de cada aislador, para un dato que el consumidor
     * ya recibe completo en el evento {@code section-insulator}.
     */
    private Map<String, Object> toSectionInsulatorPayload(SectionInsulator sectionInsulator) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", sectionInsulator.getId());
        values.put("name", sectionInsulator.getName());
        values.put("enabled", sectionInsulator.getEnabled());
        values.put("kp", sectionInsulator.getKp());
        values.put("installationType", sectionInsulator.getInstallationType() == null
                ? null
                : sectionInsulator.getInstallationType().name());
        return values;
    }


}
