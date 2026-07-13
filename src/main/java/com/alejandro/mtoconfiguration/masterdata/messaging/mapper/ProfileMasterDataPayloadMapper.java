package com.alejandro.mtoconfiguration.masterdata.messaging.mapper;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.*;
import com.alejandro.mtoconfiguration.masterdata.messaging.MasterDataEntityPayloadMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProfileMasterDataPayloadMapper implements MasterDataEntityPayloadMapper<Profile> {

    @Override
    public Class<Profile> supportedType() {
        return Profile.class;
    }

    @Override
    public Map<String, Object> toPayload(Profile profile) {
        Map<String, Object> values = new LinkedHashMap<>();

        values.put("id", profile.getId());
        values.put("profileId", profile.getProfileId());
        values.put("kp", profile.getKp());
        values.put("track", toTrackPayload(profile.getTrack()));
        values.put("anchorage", toLovPayload(profile.getAnchorage()));
        values.put("anchorageFoundation", toLovPayload(profile.getAnchorageFoundation()));
        values.put("foundation", toLovPayload(profile.getFoundation()));
        values.put("poleType", toLovPayload(profile.getPoleType()));
        values.put("portal", toLovPayload(profile.getPortal()));
        values.put("profileStatus", toLovPayload(profile.getProfileStatus()));
        values.put("returnSupport", toLovPayload(profile.getReturnSupport()));
        values.put("sectioning", toLovPayload(profile.getSectioning()));
        values.put("cantilevers", toCantileverPayload(profile.getCantilevers()));
        values.put("disconnector", toDisconnectorPayload(profile.getDisconnector()));

        return values;
    }

    private Map<String, Object> toTrackPayload(Track track) {
        if (track == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", track.getId());
        values.put("name", track.getName());
        values.put("enabled", track.getEnabled());
        values.put("stationId", track.getStation() != null ? track.getStation().getId() : null);
        values.put("executionPackageId", track.getExecutionPackage() != null ? track.getExecutionPackage().getId() : null);
        return values;
    }

    private Map<String, Object> toLovPayload(Anchorage anchorage) {
        if (anchorage == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", anchorage.getId());
        values.put("code", anchorage.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(AnchorageFoundation anchorageFoundation) {
        if (anchorageFoundation == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", anchorageFoundation.getId());
        values.put("code", anchorageFoundation.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(Foundation foundation) {
        if (foundation == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", foundation.getId());
        values.put("code", foundation.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(PoleType poleType) {
        if (poleType == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", poleType.getId());
        values.put("code", poleType.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(Portal portal) {
        if (portal == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", portal.getId());
        values.put("code", portal.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(ProfileStatus profileStatus) {
        if (profileStatus == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", profileStatus.getId());
        values.put("code", profileStatus.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(ReturnSupport returnSupport) {
        if (returnSupport == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", returnSupport.getId());
        values.put("code", returnSupport.getCode());
        return values;
    }

    private Map<String, Object> toLovPayload(Sectioning sectioning) {
        if (sectioning == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", sectioning.getId());
        values.put("code", sectioning.getCode());
        return values;
    }

    private List<Map<String, Object>> toCantileverPayload(List<Cantilever> cantilevers) {
        if (cantilevers == null) {
            return List.of();
        }

        return cantilevers.stream()
                .sorted(Comparator.comparing((Cantilever cantilever) -> cantilever.getId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toCantileverPayload)
                .toList();
    }

    private Map<String, Object> toCantileverPayload(Cantilever cantilever) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", cantilever.getId());
        values.put("cwHeight", cantilever.getCwHeight());
        values.put("stagger", cantilever.getStagger());
        values.put("catenaryHeight", cantilever.getCatenaryHeight());
        values.put("cwElevation", cantilever.getCwElevation());
        values.put("windDeflection", cantilever.getWindDeflection());
        values.put("armAngle", cantilever.getArmAngle());
        values.put("cantileverTypeId", cantilever.getCantileverType() != null ? cantilever.getCantileverType().getId() : null);
        values.put("steadyArmId", cantilever.getSteadyArm() != null ? cantilever.getSteadyArm().getId() : null);
        return values;
    }

    private Map<String, Object> toDisconnectorPayload(Disconnector disconnector) {
        if (disconnector == null) {
            return null;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", disconnector.getId());
        values.put("name", disconnector.getName());
        values.put("onLoad", disconnector.getOnLoad());
        values.put("disconnectorFunctionId", disconnector.getDisconnectorFunction() != null ? disconnector.getDisconnectorFunction().getId() : null);
        return values;
    }

}
