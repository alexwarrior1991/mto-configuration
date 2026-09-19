package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SectionInsulatorMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<SectionInsulator> {

    @Override
    public Class<SectionInsulator> supportedType() {
        return SectionInsulator.class;
    }

    @Override
    public Map<String, Object> toPayload(SectionInsulator sectionInsulator) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", sectionInsulator.getId());
        values.put("name", sectionInsulator.getName());
        values.put("enabled", sectionInsulator.getEnabled());
        values.put("kp", sectionInsulator.getKp());
        values.put("installationType", sectionInsulator.getInstallationType() == null
                ? null
                : sectionInsulator.getInstallationType().name());
        values.put("station", toStationPayload(sectionInsulator.getStation()));
        values.put("track", toTrackPayload(sectionInsulator.getTrack()));
        values.put("connectedTrack", toTrackPayload(sectionInsulator.getConnectedTrack()));
        values.put("switches", toSwitchPayload(sectionInsulator.getSwitches()));

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
        return values;
    }

    /**
     * Las agujas, en el orden en que las devuelve la colección, que es el físico a lo largo de la
     * vía ({@code @OrderBy("kp ASC, id ASC")}).
     */
    private List<Map<String, Object>> toSwitchPayload(Collection<SectionInsulatorSwitch> switches) {
        if (switches == null) {
            return List.of();
        }

        return switches.stream()
                .map(this::toSwitchPayload)
                .toList();
    }

    /**
     * La tangente viaja dos veces a propósito: {@code turnoutDenominator} es el dato —comparable y
     * ordenable— y {@code turnoutRate} es cómo está escrito en el plano, para que el consumidor no
     * tenga que componer la misma cadena. Es el mismo criterio por el que {@code sectioningFeeding}
     * del perfil sale con {@code id} y {@code code}.
     */
    private Map<String, Object> toSwitchPayload(SectionInsulatorSwitch sectionInsulatorSwitch) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", sectionInsulatorSwitch.getId());
        values.put("code", sectionInsulatorSwitch.getCode());
        values.put("kp", sectionInsulatorSwitch.getKp());
        values.put("turnoutDenominator", sectionInsulatorSwitch.getTurnoutDenominator());
        values.put("turnoutRate", sectionInsulatorSwitch.getTurnoutDenominator() == null
                ? null
                : "1:" + sectionInsulatorSwitch.getTurnoutDenominator());
        values.put("trackId", sectionInsulatorSwitch.getTrack() != null
                ? sectionInsulatorSwitch.getTrack().getId()
                : null);
        values.put("enabled", sectionInsulatorSwitch.getEnabled());
        return values;
    }
}
