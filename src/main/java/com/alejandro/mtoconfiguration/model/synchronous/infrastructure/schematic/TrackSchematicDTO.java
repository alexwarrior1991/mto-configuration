package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.schematic;

import com.alejandro.mtoconfiguration.entity.infrastructure.Cantilever;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.entity.infrastructure.Profile;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulator;
import com.alejandro.mtoconfiguration.entity.infrastructure.SectionInsulatorSwitch;
import com.alejandro.mtoconfiguration.entity.infrastructure.Station;
import com.alejandro.mtoconfiguration.entity.infrastructure.SteadyArm;
import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Una via dibujada como una linea: lo justo para pintar su esquema en una sola llamada.
 *
 * <p>Es la respuesta de {@code GET /tracks/{id}/schematic} (README_API.md §6) y a la vez lo que se
 * guarda en Redis, asi que su forma obedece a las dos cosas:
 * <ul>
 *   <li>Solo lo que un esquema enseña: codigos de catalogo en vez de {@code SLovDTO}, nada de
 *       auditoria, cimentaciones ni medidas que no se pinten. Una via de 600 perfiles con sus
 *       ménsulas cabe en unos 150 KB; el detalle completo de esos perfiles son varios MB.</li>
 *   <li>Sin {@code BigDecimal}: {@code java.math} no esta entre los {@code allowed-subtypes} del
 *       serializador de la cache (ver {@code RedisCacheConfig}), asi que los KP y las medidas viajan
 *       como texto plano ({@code "12.345"}), igual que {@code ProfileDTO.kp}.</li>
 *   <li>Listas siempre mutables ({@code ArrayList}): las inmutables del JDK no se pueden releer de
 *       la cache ({@code RedisCacheValueSerializationTest}).</li>
 *   <li>Lo que va a {@code null} no viaja ({@code NON_NULL}); al releer de la cache vuelve a ser
 *       {@code null}.</li>
 * </ul>
 *
 * <p>Los perfiles vienen en el orden fisico de la via ({@code orderInTrack}, {@code kp}, {@code id}),
 * el mismo que {@code Track.profiles}. El perfil no tiene estacion en el modelo: las de la via van en
 * {@link #stations} y la del seccionador o del aislador, en su marca.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackSchematicDTO(
        Long trackId,
        String trackName,
        Boolean enabled,
        String executionPackageName,
        List<String> stations,
        List<ProfileNode> profiles,
        List<InsulatorMark> sectionInsulators
) {

    public static TrackSchematicDTO of(Track track, List<ProfileNode> profiles, List<InsulatorMark> sectionInsulators) {
        ExecutionPackage executionPackage = track.getExecutionPackage();
        List<String> stations = track.getStations() == null
                ? new ArrayList<>()
                : track.getStations().stream()
                        .map(Station::getName)
                        .filter(Objects::nonNull)
                        .sorted()
                        .collect(Collectors.toCollection(ArrayList::new));
        return new TrackSchematicDTO(
                track.getId(),
                track.getName(),
                track.getEnabled(),
                executionPackage == null ? null : executionPackage.getName(),
                stations,
                profiles,
                sectionInsulators);
    }

    /** Un poste: sus datos principales, sus ménsulas y, si lo lleva, su seccionador. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfileNode(
            Long id,
            String code,
            String kp,
            Integer orderInTrack,
            String span,
            String poleType,
            String supportType,
            String profileStatus,
            String railPoleDistance,
            List<String> sectionings,
            List<CantileverArm> cantilevers,
            DisconnectorMark disconnector
    ) {

        public static ProfileNode of(Profile profile, List<CantileverArm> cantilevers, List<String> sectionings) {
            Disconnector disconnector = profile.getDisconnector();
            return new ProfileNode(
                    profile.getId(),
                    profile.getProfileId(),
                    plain(profile.getKp()),
                    profile.getOrderInTrack(),
                    plain(profile.getSpan()),
                    codeOf(profile.getPoleType()),
                    codeOf(profile.getSupportType()),
                    codeOf(profile.getProfileStatus()),
                    plain(profile.getRailPoleDistance()),
                    sectionings,
                    cantilevers,
                    disconnector == null ? null : DisconnectorMark.of(disconnector));
        }
    }

    /** Una ménsula con su brazo de atirantado. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CantileverArm(
            Long id,
            String type,
            String stagger,
            String cwHeight,
            String catenaryHeight,
            String steadyArmType,
            Long steadyArmLength
    ) {

        public static CantileverArm of(Cantilever cantilever) {
            SteadyArm steadyArm = cantilever.getSteadyArm();
            return new CantileverArm(
                    cantilever.getId(),
                    codeOf(cantilever.getCantileverType()),
                    plain(cantilever.getStagger()),
                    plain(cantilever.getCwHeight()),
                    plain(cantilever.getCatenaryHeight()),
                    steadyArm == null ? null : codeOf(steadyArm.getSteadyArmType()),
                    steadyArm == null ? null : steadyArm.getLength());
        }
    }

    /** El seccionador que cuelga de un poste. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DisconnectorMark(
            Long id,
            String name,
            Boolean onLoad,
            String function,
            String station
    ) {

        public static DisconnectorMark of(Disconnector disconnector) {
            return new DisconnectorMark(
                    disconnector.getId(),
                    disconnector.getName(),
                    disconnector.getOnLoad(),
                    codeOf(disconnector.getDisconnectorFunction()),
                    nameOf(disconnector.getStation()));
        }
    }

    /**
     * Un aislador de seccion en su KP. Entran los que cuelgan de la via y los que conectan con ella
     * ({@code connectedTrack}); {@code track} y {@code connectedTrack} dicen cual es cual.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InsulatorMark(
            Long id,
            String name,
            String kp,
            String installationType,
            Boolean enabled,
            String station,
            String track,
            String connectedTrack,
            List<SwitchMark> switches
    ) {

        public static InsulatorMark of(SectionInsulator insulator) {
            List<SwitchMark> switches = insulator.getSwitches() == null
                    ? new ArrayList<>()
                    : insulator.getSwitches().stream()
                            .map(SwitchMark::of)
                            .collect(Collectors.toCollection(ArrayList::new));
            return new InsulatorMark(
                    insulator.getId(),
                    insulator.getName(),
                    plain(insulator.getKp()),
                    insulator.getInstallationType() == null ? null : insulator.getInstallationType().name(),
                    insulator.getEnabled(),
                    nameOf(insulator.getStation()),
                    nameOf(insulator.getTrack()),
                    nameOf(insulator.getConnectedTrack()),
                    switches);
        }
    }

    /** Una aguja del aislador (README_API.md §4 quater). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SwitchMark(
            Long id,
            String code,
            String kp,
            Integer turnoutDenominator,
            String track
    ) {

        public static SwitchMark of(SectionInsulatorSwitch sectionInsulatorSwitch) {
            return new SwitchMark(
                    sectionInsulatorSwitch.getId(),
                    sectionInsulatorSwitch.getCode(),
                    plain(sectionInsulatorSwitch.getKp()),
                    sectionInsulatorSwitch.getTurnoutDenominator(),
                    nameOf(sectionInsulatorSwitch.getTrack()));
        }
    }

    static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    static String codeOf(Lov lov) {
        return lov == null ? null : lov.getCode();
    }

    static String nameOf(Station station) {
        return station == null ? null : station.getName();
    }

    static String nameOf(Track track) {
        return track == null ? null : track.getName();
    }
}
