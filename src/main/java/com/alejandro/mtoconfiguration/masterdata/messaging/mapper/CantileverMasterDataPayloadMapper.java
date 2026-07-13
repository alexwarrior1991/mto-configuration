package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CantileverMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<Cantilever> {

    @Override
    public Class<Cantilever> supportedType() {
        return Cantilever.class;
    }

    @Override
    public Map<String, Object> toPayload(Cantilever entity) {
        return Map.of();
    }

    private Map<String, Object> toProfilePayload(Profile profile) {
        if (profile == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", profile.getId());
        values.put("profileId", profile.getProfileId());
        values.put("kp", profile.getKp());
        values.put("trackId", profile.getTrack() != null ? profile.getTrack().getId() : null);
        return values;
    }

    private Map<String, Object> toCantileverTypePayload(CantileverType cantileverType) {
        if (cantileverType == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", cantileverType.getId());
        values.put("code", cantileverType.getCode());
        return values;
    }

    private Map<String, Object> toSteadyArmPayload(SteadyArm steadyArm) {
        if (steadyArm == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", steadyArm.getId());
        values.put("length", steadyArm.getLength());
        values.put("steadyArmTypeId", steadyArm.getSteadyArmType() != null ? steadyArm.getSteadyArmType().getId() : null);
        return values;
    }
}
