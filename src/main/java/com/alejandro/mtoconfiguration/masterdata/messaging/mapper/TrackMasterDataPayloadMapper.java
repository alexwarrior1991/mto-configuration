package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrackMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<Track> {

    @Override
    public Class<Track> supportedType() {
        return Track.class;
    }

    @Override
    public Map<String, Object> toPayload(Track track) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", track.getId());
        values.put("name", track.getName());
        values.put("enabled", track.getEnabled());
        values.put("executionPackage", toExecutionPackagePayload(track.getExecutionPackage()));
        values.put("station", toStationPayload(track.getStation()));
        values.put("profiles", toProfilePayload(track.getProfiles()));

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

    private Map<String, Object> toStationPayload(Station station) {
        if (station == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", station.getId());
        values.put("name", station.getName());
        return values;
    }

    private List<Map<String, Object>> toProfilePayload(List<Profile> profiles) {
        if (profiles == null) {
            return List.of();
        }

        return profiles.stream()
                .sorted(Comparator
                        .comparing((Profile profile) -> profile.getId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Profile::getProfileId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toProfilePayload)
                .toList();
    }

    private Map<String, Object> toProfilePayload(Profile profile) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", profile.getId());
        values.put("profileId", profile.getProfileId());
        values.put("kp", profile.getKp());
        return values;
    }
}
