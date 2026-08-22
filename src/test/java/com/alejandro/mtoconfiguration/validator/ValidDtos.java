package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DTOs que pasan la validación completa. Cada test parte de uno de estos y estropea únicamente el
 * campo que quiere comprobar, de modo que un fallo señale siempre a esa regla y no al andamiaje.
 */
public final class ValidDtos {

    private ValidDtos() {
    }

    public static SteadyArmTypeDTO steadyArmType() {
        SteadyArmTypeDTO lov = new SteadyArmTypeDTO();
        lov.setId(1L);
        lov.setCode("SA_TYPE");
        return lov;
    }

    public static CantileverTypeDTO cantileverType() {
        CantileverTypeDTO lov = new CantileverTypeDTO();
        lov.setId(2L);
        lov.setCode("CANT_TYPE");
        return lov;
    }

    public static ProfileStatusDTO profileStatus() {
        ProfileStatusDTO lov = new ProfileStatusDTO();
        lov.setId(3L);
        lov.setCode("DRAFT");
        return lov;
    }

    public static DisconnectorFunctionDTO disconnectorFunction() {
        DisconnectorFunctionDTO lov = new DisconnectorFunctionDTO();
        lov.setId(4L);
        lov.setCode("FN");
        return lov;
    }

    /** Ménsula nueva, colgando de una cantilever que aún no existe. */
    public static SteadyArmDTO newSteadyArm() {
        SteadyArmDTO dto = new SteadyArmDTO();
        dto.setLength(500L);
        dto.setSteadyArmType(steadyArmType());
        return dto;
    }

    public static SteadyArmDTO existingSteadyArm() {
        SteadyArmDTO dto = newSteadyArm();
        dto.setId(10L);
        dto.setCantileverId(20L);
        return dto;
    }

    public static CantileverDTO newCantilever() {
        CantileverDTO dto = new CantileverDTO();
        dto.setCwHeight(new BigDecimal("5.250"));
        dto.setStagger(new BigDecimal("200"));
        dto.setCatenaryHeight(new BigDecimal("1.400"));
        dto.setCwElevation(new BigDecimal("2.100"));
        dto.setWindDeflection(new BigDecimal("0.150"));
        dto.setArmAngle(new BigDecimal("12.500"));
        dto.setCantileverType(cantileverType());
        dto.setSteadyArm(existingSteadyArm());
        return dto;
    }

    public static CantileverDTO rootCantilever() {
        CantileverDTO dto = newCantilever();
        dto.setProfileId(30L);
        return dto;
    }

    public static DisconnectorDTO newDisconnector() {
        DisconnectorDTO dto = new DisconnectorDTO();
        dto.setName("Seccionador 1");
        dto.setOnLoad(Boolean.TRUE);
        dto.setDisconnectorFunction(disconnectorFunction());
        return dto;
    }

    public static DisconnectorDTO rootDisconnector() {
        DisconnectorDTO dto = newDisconnector();
        dto.setStationId(40L);
        dto.setProfileId(30L);
        return dto;
    }

    public static SectionInsulatorDTO newSectionInsulator() {
        SectionInsulatorDTO dto = new SectionInsulatorDTO();
        dto.setName("Aislador 1");
        dto.setEnabled(Boolean.TRUE);
        return dto;
    }

    public static SectionInsulatorDTO rootSectionInsulator() {
        SectionInsulatorDTO dto = newSectionInsulator();
        dto.setStationId(40L);
        return dto;
    }

    public static ProfileDTO newProfile() {
        ProfileDTO dto = new ProfileDTO();
        dto.setProfileId("P-0001");
        dto.setKp("12345.678");
        dto.setProfileStatus(profileStatus());
        dto.setCantilevers(new ArrayList<>());
        return dto;
    }

    public static ProfileDTO rootProfile() {
        ProfileDTO dto = newProfile();
        dto.setTrackId(50L);
        return dto;
    }

    public static TrackDTO newTrack() {
        TrackDTO dto = new TrackDTO();
        dto.setName("Vía 1");
        dto.setEnabled(Boolean.TRUE);
        dto.setProfiles(new ArrayList<>());
        return dto;
    }

    public static TrackDTO rootTrack() {
        TrackDTO dto = newTrack();
        dto.setExecutionPackageId(60L);
        return dto;
    }

    public static StationDTO newStation() {
        StationDTO dto = new StationDTO();
        dto.setName("Estación 1");
        dto.setTracks(new ArrayList<>());
        dto.setDisconnectors(new ArrayList<>());
        dto.setSectionInsulators(new ArrayList<>());
        return dto;
    }

    public static StationDTO rootStation() {
        StationDTO dto = newStation();
        dto.setExecutionPackageId(60L);
        return dto;
    }

    public static ExecutionPackageDTO rootExecutionPackage() {
        ExecutionPackageDTO dto = new ExecutionPackageDTO();
        dto.setName("Paquete 1");
        dto.setInitialPackage(Boolean.FALSE);
        dto.setLength(1_000L);
        dto.setStartDate(LocalDate.of(2026, 1, 1));
        dto.setEndDate(LocalDate.of(2026, 12, 31));
        dto.setCompanyId(70L);
        dto.setTracks(new ArrayList<>());
        dto.setStations(new ArrayList<>());
        return dto;
    }

    /** Repite una cadena hasta la longitud pedida, para probar los límites de longitud. */
    public static String text(int length) {
        return "x".repeat(length);
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... items) {
        return new ArrayList<>(List.of(items));
    }
}
