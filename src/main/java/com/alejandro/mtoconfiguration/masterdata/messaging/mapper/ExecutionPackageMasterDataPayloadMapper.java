package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ExecutionPackageMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<ExecutionPackage> {

    @Override
    public Class<ExecutionPackage> supportedType() {
        return ExecutionPackage.class;
    }

    @Override
    public Map<String, Object> toPayload(ExecutionPackage executionPackage) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", executionPackage.getId());
        values.put("name", executionPackage.getName());
        values.put("initialPackage", executionPackage.getInitialPackage());
        values.put("length", executionPackage.getLength());
        values.put("startDate", executionPackage.getStartDate());
        values.put("endDate", executionPackage.getEndDate());
        values.put("enabled", executionPackage.isEnabled());
        values.put("company", toCompanyPayload(executionPackage.getCompany()));
        values.put("tracks", toTrackPayload(executionPackage.getTracks()));
        values.put("stations", toStationPayload(executionPackage.getStations()));

        return values;
    }

    private Map<String, Object> toCompanyPayload(BusinessEntity company) {
        if (company == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", company.getId());
        values.put("code", company.getCode());
        values.put("name", company.getName());
        values.put("identificationNumber", company.getIdentificationNumber());
        return values;
    }

    private List<Map<String, Object>> toTrackPayload(Set<Track> tracks) {
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
        values.put("stationId", track.getStation() != null ? track.getStation().getId() : null);
        return values;
    }

    private List<Map<String, Object>> toStationPayload(Set<Station> stations) {
        if (stations == null) {
            return List.of();
        }

        return stations.stream()
                .sorted(Comparator
                        .comparing((Station station) -> station.getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Station::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toStationPayload)
                .toList();
    }

    private Map<String, Object> toStationPayload(Station station) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", station.getId());
        values.put("name", station.getName());
        return values;
    }
}
