package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.lov.DisconnectorFunction;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DisconnectorMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<Disconnector> {

    @Override
    public Class<Disconnector> supportedType() {
        return Disconnector.class;
    }

    @Override
    public Map<String, Object> toPayload(Disconnector disconnector) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", disconnector.getId());
        values.put("name", disconnector.getName());
        values.put("onLoad", disconnector.getOnLoad());
        values.put("station", toStationPayload(disconnector.getStation()));
        values.put("profile", toProfilePayload(disconnector.getProfile()));
        values.put("disconnectorFunction", toDisconnectorFunctionPayload(disconnector.getDisconnectorFunction()));

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

    private Map<String, Object> toProfilePayload(Profile profile) {
        if (profile == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", profile.getId());
        values.put("profileId", profile.getProfileId());
        values.put("kp", profile.getKp());
        return values;
    }

    private Map<String, Object> toDisconnectorFunctionPayload(DisconnectorFunction disconnectorFunction) {
        if (disconnectorFunction == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", disconnectorFunction.getId());
        values.put("code", disconnectorFunction.getCode());
        return values;
    }

}
