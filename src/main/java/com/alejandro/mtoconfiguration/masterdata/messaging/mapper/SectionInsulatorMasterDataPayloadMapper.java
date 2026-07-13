package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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
        values.put("station", toStationPayload(sectionInsulator.getStation()));

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
}
