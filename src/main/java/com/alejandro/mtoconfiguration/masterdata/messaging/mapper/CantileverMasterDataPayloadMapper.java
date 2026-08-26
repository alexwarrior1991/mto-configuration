package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El mapper existia pero {@code toPayload} devolvia {@code Map.of()}: los eventos de
 * Cantilever viajaban con el payload VACIO y los metodos de abajo eran codigo muerto.
 * El consumidor recibia un cambio sin ningun dato con el que actuar.
 * <p>
 * Del steadyArm solo se publican campos escalares, sin volver al cantilever, para no
 * reintroducir el ciclo Cantilever <-> SteadyArm.
 */
@Component
public class CantileverMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<Cantilever> {


    @Override
    public Class<Cantilever> supportedType() {
        return Cantilever.class;
    }

    @Override
    public Map<String, Object> toPayload(Cantilever cantilever) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", cantilever.getId());
        values.put("cwHeight", cantilever.getCwHeight());
        values.put("stagger", cantilever.getStagger());
        values.put("catenaryHeight", cantilever.getCatenaryHeight());
        values.put("cwElevation", cantilever.getCwElevation());
        values.put("windDeflection", cantilever.getWindDeflection());
        values.put("armAngle", cantilever.getArmAngle());
        values.put("cantileverType", toCantileverTypePayload(cantilever.getCantileverType()));
        values.put("profile", toProfilePayload(cantilever.getProfile()));
        values.put("steadyArm", toSteadyArmPayload(cantilever.getSteadyArm()));

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
