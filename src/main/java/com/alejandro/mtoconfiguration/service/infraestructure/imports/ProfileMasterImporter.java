package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.GenericException;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.DisconnectorMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.SectionInsulatorMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.SectionInsulatorSwitchMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.StationMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.TrackMasterRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Carga {@code profile-master.xlsx} en base de datos.
 *
 * <h2>El orden no es negociable</h2>
 *
 * <p>{@code EPS -> STATIONS -> TRACKS -> PROFILES (+ CANTILEVERS) -> SECTION_INSULATORS
 * (+ SWITCHES) -> DISCONNECTORS}. Cada nivel necesita el identificador del anterior, que solo
 * se conoce despues de escribirlo. Una vía sin estación es correcta —la columna es anulable a
 * proposito— pero una vía sin paquete no.
 *
 * <h2>Qué pasa cuando falta el padre</h2>
 *
 * <p>Si un paquete falla, sus estaciones, vías y perfiles no se intentan: se cuentan como
 * error una sola vez por elemento, con el motivo real ("su paquete EP6 no se ha podido
 * cargar") en lugar de repetir 1.947 violaciones de clave ajena que solo dicen que algo
 * fue mal más arriba.
 *
 * <p>En simulacion no hay identificadores porque no se escribe nada, asi que los hijos se
 * cuentan igual pero no se intentan escribir. El informe sale con los mismos recuentos
 * que la carga real, que es lo unico que hace util un {@code dryRun}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMasterImporter {

    private final ProfileMasterParser parser;
    private final InfrastructureUpsertService upsertService;

    public ProfileImportReport importFrom(InputStream inputStream, boolean dryRun) {
        return importFrom(inputStream, dryRun, ignored -> { });
    }

    /**
     * @param progress recibe un booleano por elemento procesado: cierto si fue bien. Lo
     *                 usa el trabajo de fondo para ir publicando avance.
     */
    public ProfileImportReport importFrom(InputStream inputStream, boolean dryRun,
                                          Consumer<Boolean> progress) {
        ProfileMasterParser.ProfileMasterContent content = parser.parseAll(inputStream);
        ProfileImportReport report = new ProfileImportReport(dryRun);

        Map<String, Long> packagesByCode = importExecutionPackages(content, dryRun, report, progress);
        Map<StationKey, Long> stationsByKey = importStations(content, packagesByCode, dryRun, report, progress);
        Map<TrackKey, Long> tracksByKey = importTracks(content, packagesByCode, stationsByKey, dryRun,
                report, progress);
        Map<Integer, Long> profileIdsByRow = importProfiles(content, tracksByKey, dryRun, report, progress);
        importSectionInsulators(content, stationsByKey, tracksByKey, dryRun, report, progress);
        importDisconnectors(content, packagesByCode, stationsByKey, tracksByKey, profileIdsByRow, dryRun,
                report, progress);

        log.info("Importacion del maestro de perfiles terminada dryRun={} altas={} modificaciones={} "
                        + "mensulas={} agujas={} errores={} omitidos={}",
                dryRun, report.getCreated(), report.getUpdated(), report.getCantileversWritten(),
                report.getSwitchesWritten(), report.getFailed(), report.getSkippedDisabled());
        return report;
    }

    private Map<String, Long> importExecutionPackages(ProfileMasterParser.ProfileMasterContent content,
                                                      boolean dryRun, ProfileImportReport report,
                                                      Consumer<Boolean> progress) {
        Map<String, Long> byCode = new HashMap<>();
        for (ExecutionPackageMasterRow row : content.executionPackages()) {
            if (!row.enabled()) {
                report.skipDisabled();
                continue;
            }
            try {
                var result = upsertService.upsertExecutionPackage(row, dryRun);
                count(report, ProfileImportReport.EXECUTION_PACKAGE, result.outcome());
                byCode.put(key(row.code()), result.id());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.EXECUTION_PACKAGE, row.code(), e, progress);
            }
        }
        return byCode;
    }

    private Map<StationKey, Long> importStations(ProfileMasterParser.ProfileMasterContent content,
                                                 Map<String, Long> packagesByCode, boolean dryRun,
                                                 ProfileImportReport report, Consumer<Boolean> progress) {
        Map<StationKey, Long> byKey = new HashMap<>();
        for (StationMasterRow row : content.stations()) {
            String code = key(row.executionPackage());
            if (!packagesByCode.containsKey(code)) {
                fail(report, row.sourceRow(), ProfileImportReport.STATION, reference(row),
                        "su paquete " + row.executionPackage() + " no se ha podido cargar", progress);
                continue;
            }
            try {
                var result = upsertService.upsertStation(row, packagesByCode.get(code), dryRun);
                count(report, ProfileImportReport.STATION, result.outcome());
                byKey.put(new StationKey(code, key(row.name())), result.id());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.STATION, reference(row), e, progress);
            }
        }
        return byKey;
    }

    private Map<TrackKey, Long> importTracks(ProfileMasterParser.ProfileMasterContent content,
                                             Map<String, Long> packagesByCode,
                                             Map<StationKey, Long> stationsByKey, boolean dryRun,
                                             ProfileImportReport report, Consumer<Boolean> progress) {
        Map<TrackKey, Long> byKey = new HashMap<>();
        for (TrackMasterRow row : content.tracks()) {
            String code = key(row.executionPackage());
            if (!packagesByCode.containsKey(code)) {
                fail(report, row.sourceRow(), ProfileImportReport.TRACK, reference(row),
                        "su paquete " + row.executionPackage() + " no se ha podido cargar", progress);
                continue;
            }
            if (!row.enabled()) {
                report.skipDisabled();
                continue;
            }

            // Una via sin estaciones es correcta; una que declara una que no existe, no. Se
            // acumulan TODAS las que fallan antes de rendirse: enterarse de las tres de golpe
            // vale mas que arreglar una, reimportar, y descubrir la siguiente.
            List<Long> stationIds = new ArrayList<>();
            List<String> desconocidas = new ArrayList<>();
            for (String station : row.stations()) {
                Long stationId = stationsByKey.get(new StationKey(code, key(station)));
                if (stationId == null) {
                    desconocidas.add(station);
                } else {
                    stationIds.add(stationId);
                }
            }
            if (!desconocidas.isEmpty() && !dryRun) {
                fail(report, row.sourceRow(), ProfileImportReport.TRACK, reference(row),
                        "declara " + (desconocidas.size() == 1 ? "la estacion " : "las estaciones ")
                                + desconocidas.stream().collect(Collectors.joining("', '", "'", "'"))
                                + ", que no " + (desconocidas.size() == 1 ? "esta" : "estan")
                                + " en la hoja STATIONS",
                        progress);
                continue;
            }

            try {
                var result = upsertService.upsertTrack(row, packagesByCode.get(code), stationIds, dryRun);
                count(report, ProfileImportReport.TRACK, result.outcome());
                byKey.put(new TrackKey(code, key(row.name())), result.id());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.TRACK, reference(row), e, progress);
            }
        }
        return byKey;
    }

    /**
     * @return el id de cada perfil escrito, por su fila de la hoja PROFILES: es como los busca
     *         {@link #importDisconnectors}. En simulacion, el de un alta es null, pero la fila esta
     */
    private Map<Integer, Long> importProfiles(ProfileMasterParser.ProfileMasterContent content,
                                              Map<TrackKey, Long> tracksByKey, boolean dryRun,
                                              ProfileImportReport report, Consumer<Boolean> progress) {
        Map<Integer, Long> idsByRow = new HashMap<>();
        // Las mensulas se agrupan de una vez: buscarlas por perfil dentro del bucle seria
        // recorrer 14.592 filas por cada uno de los 11.715 perfiles.
        Map<ProfileKey, List<CantileverMasterRow>> cantileversByProfile = content.cantilevers().stream()
                .filter(CantileverMasterRow::enabled)
                .collect(Collectors.groupingBy(row -> new ProfileKey(
                        key(row.executionPackage()), key(row.track()), row.orderInTrack())));

        reportOrphanCantilevers(content, cantileversByProfile, report, progress);

        for (ProfileMasterRow row : content.profiles()) {
            if (!row.enabled()) {
                report.skipDisabled();
                continue;
            }

            TrackKey trackKey = new TrackKey(key(row.executionPackage()), key(row.track()));
            if (!tracksByKey.containsKey(trackKey)) {
                fail(report, row.sourceRow(), ProfileImportReport.PROFILE, reference(row),
                        "su via '" + row.track() + "' no se ha podido cargar", progress);
                continue;
            }

            // Por ORDEN y no por identificador: una via con dos tramos concatenados repite el
            // identificador de perfil, asi que agrupar por el juntaria las mensulas de los DOS
            // y cada perfil se llevaria hasta seis, el doble del maximo que admite.
            List<CantileverMasterRow> cantilevers = cantileversByProfile.getOrDefault(
                    new ProfileKey(trackKey.executionPackage(), trackKey.name(), row.orderInTrack()),
                    List.of());

            try {
                var result = upsertService.upsertProfile(row, tracksByKey.get(trackKey), cantilevers, dryRun);
                count(report, ProfileImportReport.PROFILE, result.outcome());
                report.addCantilevers(cantilevers.size());
                idsByRow.put(row.sourceRow(), result.id());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.PROFILE, reference(row), e, progress);
            }
        }
        return idsByRow;
    }

    /**
     * Las mensulas que no casaron con ningun perfil.
     *
     * <p>El mismo agujero que tenian las agujas, y se tapa igual: una fila cuyo ORDEN no existe en
     * esa via se quedaba fuera de la importacion sin que nada lo dijera, y el perfil aparecia con
     * una mensula de menos que nadie iba a echar en falta hasta mirar el poste.
     *
     * <p>Se empareja por ORDEN y no por identificador de perfil —una via con dos tramos
     * concatenados repite el identificador—, asi que el mensaje dice el orden y la via, que es lo
     * que hay que buscar en la hoja para corregirlo.
     *
     * <p>Contra <b>todas</b> las filas de perfiles, no contra las escritas: un perfil deshabilitado
     * o que fallo por su via ya tiene su linea en el informe, y repetirla por cada una de sus
     * mensulas seria ruido sobre un problema ya contado. Las mensulas deshabilitadas no llegan
     * aqui: el agrupamiento las filtra antes, que en un hijo cuyo ENABLED no viaja a ninguna parte
     * sigue siendo lo que se hace.
     */
    private void reportOrphanCantilevers(ProfileMasterParser.ProfileMasterContent content,
                                         Map<ProfileKey, List<CantileverMasterRow>> cantileversByProfile,
                                         ProfileImportReport report, Consumer<Boolean> progress) {

        Set<ProfileKey> declarados = content.profiles().stream()
                .map(row -> new ProfileKey(
                        key(row.executionPackage()), key(row.track()), row.orderInTrack()))
                .collect(Collectors.toSet());

        cantileversByProfile.forEach((profileKey, rows) -> {
            if (declarados.contains(profileKey)) {
                return;
            }
            for (CantileverMasterRow row : rows) {
                fail(report, row.sourceRow(), ProfileImportReport.PROFILE,
                        row.executionPackage() + " / " + row.track() + " / " + row.profileId()
                                + " / SLOT " + row.slot(),
                        "su perfil no esta en la hoja " + ProfileMasterParser.PROFILES_SHEET
                                + ": ninguna fila con ORDEN " + row.orderInTrack()
                                + " en la via '" + row.track() + "'",
                        progress);
            }
        });
    }

    /**
     * Los aisladores de seccion van al final, y no por capricho: uno puede nombrar hasta dos vias,
     * y la via solo tiene identificador despues de escribirla.
     *
     * <p>La vía que un aislador nombra y no existe se deja a null en lugar de tumbar la fila: la
     * columna es anulable, un aislador sin vía sigue siendo un aislador de su estacion, y tirar la
     * fila entera perderia tambien sus agujas. La estacion si es obligatoria, y sin ella no hay
     * donde colgarlo.
     */
    private void importSectionInsulators(ProfileMasterParser.ProfileMasterContent content,
                                         Map<StationKey, Long> stationsByKey,
                                         Map<TrackKey, Long> tracksByKey, boolean dryRun,
                                         ProfileImportReport report, Consumer<Boolean> progress) {

        if (content.sectionInsulators().isEmpty() && content.sectionInsulatorSwitches().isEmpty()) {
            return;
        }

        // Las agujas se agrupan de una vez, por lo mismo que las mensulas: buscarlas dentro del
        // bucle seria recorrer la hoja entera por cada aislador.
        //
        // Y SIN filtrar por ENABLED, al contrario que las mensulas. No es un descuido: en un hijo
        // que se reconcilia con mergeCollection, dejar la fila fuera de la lista no significa "no
        // la cargues", significa BORRARLA, con su id y su historico de auditoria. Una aguja fuera
        // de servicio sigue estando en el plano, y el equipo que va de noche necesita verla marcada
        // y no que desaparezca; por eso entra deshabilitada y viaja asi hasta el parte de turno de
        // mto-maintenance, que la escribe 'W31 1:9 (out of service)'. Lo que borra una aguja es
        // quitar su fila de la hoja.
        Map<SectionInsulatorKey, List<SectionInsulatorSwitchMasterRow>> switchesByInsulator =
                content.sectionInsulatorSwitches().stream()
                        .collect(Collectors.groupingBy(row -> new SectionInsulatorKey(
                                key(row.executionPackage()), key(row.station()), key(row.sectionInsulator()))));

        reportOrphanSwitches(content, switchesByInsulator, report, progress);

        if (content.sectionInsulators().isEmpty()) {
            return;
        }

        // Las vias del paquete, por nombre, para resolver las que nombran el aislador y sus agujas.
        Map<String, Map<String, Long>> tracksByPackage = tracksByPackage(tracksByKey);

        for (SectionInsulatorMasterRow row : content.sectionInsulators()) {
            if (!row.enabled()) {
                report.skipDisabled();
                continue;
            }

            String code = key(row.executionPackage());
            StationKey stationKey = new StationKey(code, key(row.station()));
            if (!stationsByKey.containsKey(stationKey)) {
                fail(report, row.sourceRow(), ProfileImportReport.SECTION_INSULATOR, reference(row),
                        "su estacion '" + row.station() + "' no se ha podido cargar", progress);
                continue;
            }

            Map<String, Long> tracksOfPackage = tracksByPackage.getOrDefault(code, Map.of());
            List<SectionInsulatorSwitchMasterRow> switches = switchesByInsulator.getOrDefault(
                    new SectionInsulatorKey(code, key(row.station()), key(row.name())), List.of());

            try {
                var result = upsertService.upsertSectionInsulator(row, stationsByKey.get(stationKey),
                        tracksOfPackage.get(key(row.track())),
                        tracksOfPackage.get(key(row.connectedTrack())),
                        switches, tracksOfPackage, dryRun);
                count(report, ProfileImportReport.SECTION_INSULATOR, result.outcome());
                report.addSwitches(switches.size());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.SECTION_INSULATOR, reference(row), e, progress);
            }
        }
    }

    /**
     * Las agujas cuyo AISLADOR no esta en la hoja de aisladores.
     *
     * <p>Una errata en ese nombre dejaba la aguja fuera de la importacion <b>sin que nada lo
     * dijera</b>: no casa con ningun aislador, nadie la consume y el informe sale limpio. El dia de
     * la carga faltaria una aguja y no habria por donde empezar a buscar. Ahora cada una sale como
     * error con su fila de origen y con el nombre que no se encontro, que es lo que hace falta para
     * corregir la celda.
     *
     * <p>Se mira contra <b>todas</b> las filas de aisladores, no contra las que se llegaron a
     * escribir: un aislador deshabilitado o que fallo por su estacion ya tiene su propia linea en el
     * informe, y repetirla por cada una de sus agujas seria ruido sobre un problema ya contado.
     */
    private void reportOrphanSwitches(ProfileMasterParser.ProfileMasterContent content,
                                      Map<SectionInsulatorKey, List<SectionInsulatorSwitchMasterRow>> switchesByInsulator,
                                      ProfileImportReport report, Consumer<Boolean> progress) {

        Set<SectionInsulatorKey> declarados = content.sectionInsulators().stream()
                .map(row -> new SectionInsulatorKey(
                        key(row.executionPackage()), key(row.station()), key(row.name())))
                .collect(Collectors.toSet());

        switchesByInsulator.forEach((insulatorKey, rows) -> {
            if (declarados.contains(insulatorKey)) {
                return;
            }
            for (SectionInsulatorSwitchMasterRow row : rows) {
                fail(report, row.sourceRow(), ProfileImportReport.SECTION_INSULATOR,
                        row.sectionInsulator() + " / " + row.code(),
                        "su aislador '" + row.sectionInsulator() + "' no esta en la hoja "
                                + ProfileMasterParser.SECTION_INSULATORS_SHEET,
                        progress);
            }
        });
    }

    /**
     * Los seccionadores van los ultimos: su estacion, su poste y, sin poste, su via tienen que estar
     * ya escritos.
     *
     * <p>El poste se busca en la hoja PROFILES, por su via y su identificador, y no en la base: asi la
     * simulacion, que no escribe, encuentra los mismos que la carga real, y un poste deshabilitado o
     * que fallo no se confunde con uno que no existe. Una via de dos tramos concatenados repite
     * identificadores —el caso de EP9A que obliga a casar las mensulas por ORDEN—, y ahi decide
     * KP_POSTE ({@link #findPole}).
     *
     * <p>La estacion es opcional: {@code SIN ESTACION} en ESTACION dice que no es de ninguna (uno en
     * plena via, en una zona neutra o en una subestacion). En blanco no vale, porque un olvido se
     * cargaria sin estacion. Por eso el seccionador se identifica por su paquete y su nombre
     * ({@code InfrastructureUpsertService.upsertDisconnector}), y dos filas con el mismo nombre en el
     * mismo paquete se señalan aqui, como dos en el mismo poste.
     *
     * <p>Sin poste, la via que la fila nombra y no existe se deja a null, como la de un aislador: la
     * columna es anulable y el seccionador sigue siendo de su estacion. Sin estacion no: su via es lo
     * unico que lo situa, y tiene que estar entre las cargadas de su paquete.
     *
     * <p>La via conectada (VIA_CONECTADA, V27) no: es lo unico que dice que el seccionador pone dos
     * vias en paralelo, y perderla en silencio dejaria la carga contando una fila que no dice lo que
     * la hoja. Una que no es de su paquete, o que es la propia via de la fila, la señala en el
     * informe, igual en la simulacion que en la carga real ({@link #connectedTrack}).
     *
     * @param profileIdsByRow lo que devuelve {@link #importProfiles}
     */
    private void importDisconnectors(ProfileMasterParser.ProfileMasterContent content,
                                     Map<String, Long> packagesByCode,
                                     Map<StationKey, Long> stationsByKey,
                                     Map<TrackKey, Long> tracksByKey,
                                     Map<Integer, Long> profileIdsByRow, boolean dryRun,
                                     ProfileImportReport report, Consumer<Boolean> progress) {
        if (content.disconnectors().isEmpty()) {
            return;
        }

        Map<PoleKey, List<ProfileMasterRow>> poles = content.profiles().stream()
                .collect(Collectors.groupingBy(row -> new PoleKey(
                        key(row.executionPackage()), key(row.track()), key(row.profileId()))));
        // Que fila de la hoja se ha llevado ya cada poste: un poste admite un solo seccionador, y
        // dos filas en el mismo se señalan aqui, con el nombre de la otra, antes de que el indice
        // unico conteste por ellas.
        Map<Integer, String> claimedBy = new HashMap<>();
        // Que fila se ha llevado ya cada nombre de cada paquete, que es la clave del seccionador: la
        // segunda se señala con la primera, en vez de pisarla.
        Map<String, Integer> rowOfName = new HashMap<>();
        Map<String, Map<String, Long>> tracksByPackage = tracksByPackage(tracksByKey);

        for (DisconnectorMasterRow row : content.disconnectors()) {
            if (!row.enabled()) {
                report.skipDisabled();
                continue;
            }

            String code = key(row.executionPackage());
            if (!packagesByCode.containsKey(code)) {
                fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                        "su paquete '" + row.executionPackage() + "' no se ha podido cargar", progress);
                continue;
            }

            String station = key(row.station());
            if (station.isEmpty()) {
                fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                        "ESTACION en blanco: escribe su estacion, o " + WITHOUT_STATION
                                + " si no es de ninguna", progress);
                continue;
            }
            boolean withoutStation = isWithoutStation(station);
            StationKey stationKey = new StationKey(code, station);
            if (!withoutStation && !stationsByKey.containsKey(stationKey)) {
                fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                        "su estacion '" + row.station() + "' no se ha podido cargar", progress);
                continue;
            }

            if (!key(row.name()).isEmpty()) {
                Integer first = rowOfName.putIfAbsent(code + "|" + key(row.name()), row.sourceRow());
                if (first != null) {
                    fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                            "su nombre ya lo lleva la fila " + first + " del paquete "
                                    + row.executionPackage() + ", y el seccionador se identifica por su "
                                    + "paquete y su nombre", progress);
                    continue;
                }
            }

            // Antes que el poste: una fila que falla aqui no puede quedarse con el poste de otra.
            ConnectedTrack connected = connectedTrack(row, tracksByPackage.getOrDefault(code, Map.of()));
            if (connected.problem() != null) {
                fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                        connected.problem(), progress);
                continue;
            }

            Long profileId = null;
            Long trackId = null;
            if (key(row.profileId()).isEmpty()) {
                TrackKey ownTrack = new TrackKey(code, key(row.track()));
                if (withoutStation && !tracksByKey.containsKey(ownTrack)) {
                    fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                            key(row.track()).isEmpty()
                                    ? "no es de ninguna estacion, no esta en un poste y no dice su VIA: "
                                    + "no quedaria en ningun sitio"
                                    : "no es de ninguna estacion ni esta en un poste, y su VIA '" + row.track()
                                    + "' no esta entre las vias cargadas del paquete "
                                    + row.executionPackage() + ": no quedaria en ningun sitio",
                            progress);
                    continue;
                }
                trackId = tracksByKey.get(ownTrack);
            } else {
                PoleMatch match = findPole(row, poles.getOrDefault(
                        new PoleKey(code, key(row.track()), key(row.profileId())), List.of()));
                if (match.pole() == null) {
                    fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                            match.problem(), progress);
                    continue;
                }
                int poleRow = match.pole().sourceRow();
                if (!profileIdsByRow.containsKey(poleRow)) {
                    fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                            "su poste '" + row.profileId() + "' de la via '" + row.track()
                                    + "' no se ha podido cargar", progress);
                    continue;
                }
                String other = claimedBy.putIfAbsent(poleRow, row.name());
                if (other != null) {
                    fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row),
                            "su poste '" + row.profileId() + "' ya lo lleva '" + other
                                    + "' en esta misma hoja, y un poste admite un solo seccionador",
                            progress);
                    continue;
                }
                profileId = profileIdsByRow.get(poleRow);
            }

            try {
                var result = upsertService.upsertDisconnector(row,
                        withoutStation ? null : stationsByKey.get(stationKey), packagesByCode.get(code),
                        profileId, trackId, connected.id(), dryRun);
                count(report, ProfileImportReport.DISCONNECTOR, result.outcome());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.DISCONNECTOR, reference(row), e, progress);
            }
        }
    }

    /**
     * El poste de la fila entre los que la hoja PROFILES tiene en su via con su identificador.
     *
     * <p>Uno solo es el caso normal, y entonces KP_POSTE no se mira: el identificador basta, y quien
     * cambia de poste en la hoja no tiene por que acordarse de cambiar tambien su KP. Varios es una
     * via de dos tramos concatenados, y ahi KP_POSTE es lo unico que los distingue.
     */
    private PoleMatch findPole(DisconnectorMasterRow row, List<ProfileMasterRow> candidates) {
        if (candidates.isEmpty()) {
            return PoleMatch.notFound("su poste '" + row.profileId() + "' no esta en la via '"
                    + row.track() + "' de la hoja " + ProfileMasterParser.PROFILES_SHEET);
        }
        if (candidates.size() == 1) {
            return PoleMatch.found(candidates.getFirst());
        }

        String kps = candidates.stream().map(ProfileMasterRow::kp).collect(Collectors.joining(", "));
        if (row.profileKp() == null) {
            return PoleMatch.notFound("la via '" + row.track() + "' tiene " + candidates.size()
                    + " postes '" + row.profileId() + "', en los KP " + kps
                    + ", y KP_POSTE no dice cual es el suyo");
        }
        return candidates.stream()
                .filter(candidate -> sameKp(candidate.kp(), row.profileKp()))
                .findFirst()
                .map(PoleMatch::found)
                .orElseGet(() -> PoleMatch.notFound("ninguno de los " + candidates.size() + " postes '"
                        + row.profileId() + "' de la via '" + row.track() + "' esta en el KP_POSTE "
                        + row.profileKp().toPlainString() + ": estan en los KP " + kps));
    }

    /**
     * La via conectada de la fila, dentro de su paquete, como la del aislador.
     *
     * <p>En blanco no hay ninguna. Una que no esta entre las vias que esta carga ha escrito de su
     * paquete (no existe, o su fila de TRACKS esta deshabilitada o ha fallado), o que es la VIA de la
     * fila (la del poste, o la propia sin el), es un problema de la fila: se compara por nombre, que
     * es lo que hay en la simulacion, donde una via que se da de alta en esta misma carga no tiene id.
     */
    private ConnectedTrack connectedTrack(DisconnectorMasterRow row, Map<String, Long> tracksOfPackage) {
        String name = key(row.connectedTrack());
        if (name.isEmpty()) {
            return ConnectedTrack.NONE;
        }
        if (!tracksOfPackage.containsKey(name)) {
            return ConnectedTrack.problem("su VIA_CONECTADA '" + row.connectedTrack().trim()
                    + "' no esta entre las vias cargadas del paquete " + row.executionPackage());
        }
        if (name.equals(key(row.track()))) {
            return ConnectedTrack.problem("su VIA_CONECTADA '" + row.connectedTrack().trim()
                    + "' es su propia via: tiene que ser la otra de las dos que pone en paralelo");
        }
        return new ConnectedTrack(tracksOfPackage.get(name), null);
    }

    /** Las vias de cada paquete, por nombre: con ellas se resuelven las que nombra una fila. */
    private static Map<String, Map<String, Long>> tracksByPackage(Map<TrackKey, Long> tracksByKey) {
        Map<String, Map<String, Long>> tracksByPackage = new HashMap<>();
        tracksByKey.forEach((trackKey, trackId) -> tracksByPackage
                .computeIfAbsent(trackKey.executionPackage(), ignored -> new HashMap<>())
                .put(trackKey.name(), trackId));
        return tracksByPackage;
    }

    /** {@code 98375.5} y {@code 98375.500} son el mismo KP; uno que no es un numero no casa con nada. */
    private static boolean sameKp(String kp, BigDecimal expected) {
        try {
            return kp != null && !kp.isBlank() && new BigDecimal(kp.trim()).compareTo(expected) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void count(ProfileImportReport report, String entity,
                       InfrastructureUpsertService.UpsertResult.Outcome outcome) {
        switch (outcome) {
            case CREATED -> report.outcomeOf(entity).create();
            case UPDATED -> report.outcomeOf(entity).update();
            case UNCHANGED -> report.outcomeOf(entity).unchanged();
        }
    }

    private void fail(ProfileImportReport report, int row, String entity, String reference,
                      Exception e, Consumer<Boolean> progress) {
        log.warn("Fila {} de {} fallida ({}): {}", row, entity, reference, e.toString());
        fail(report, row, entity, reference, messageOf(e), progress);
    }

    private void fail(ProfileImportReport report, int row, String entity, String reference,
                      String message, Consumer<Boolean> progress) {
        report.addError(row, entity, reference, message);
        progress.accept(false);
    }

    /**
     * Mensaje legible del fallo.
     *
     * <p>Las excepciones de negocio llevan sus alertas por campo, que es justo lo que
     * necesita quien tiene que corregir la fila del maestro: se juntan en una linea en
     * lugar de quedarse con {@code getMessage()}, que solo trae la primera.
     */
    private String messageOf(Exception e) {
        if (e instanceof BaseException baseException && !baseException.getErrors().isEmpty()) {
            return baseException.getErrors().stream().map(this::describe).collect(Collectors.joining("; "));
        }
        if (e instanceof GenericException generic && generic.getCode() != null) {
            return generic.getCode();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private String describe(Alert alert) {
        return alert.getFields() == null || alert.getFields().isEmpty()
                ? alert.getMessage()
                : alert.getMessage() + " " + alert.getFields();
    }

    private String reference(StationMasterRow row) {
        return row.executionPackage() + " / " + row.name();
    }

    private String reference(TrackMasterRow row) {
        return row.executionPackage() + " / " + row.name();
    }

    private String reference(ProfileMasterRow row) {
        return row.executionPackage() + " / " + row.track() + " / " + row.profileId();
    }

    private String reference(SectionInsulatorMasterRow row) {
        return row.executionPackage() + " / " + row.station() + " / " + row.name();
    }

    /** Su clave natural, paquete y nombre: la estacion es opcional y no lo identifica. */
    private String reference(DisconnectorMasterRow row) {
        return row.executionPackage() + " / " + row.name();
    }

    /**
     * Lo que dice en ESTACION que un seccionador no es de ninguna estacion, con o sin tilde. Va
     * escrito, y no en blanco, para que un olvido no se cargue como «sin estacion».
     */
    static final String WITHOUT_STATION = "SIN ESTACION";

    private boolean isWithoutStation(String stationKey) {
        return WITHOUT_STATION.equals(stationKey) || "SIN ESTACIÓN".equals(stationKey);
    }

    /**
     * Las claves se comparan en mayusculas, igual que los indices unicos de V12: el
     * origen no es consistente y {@code HR TRACK 3 HAD} convive con {@code HR Track 3 BIN}.
     */
    private String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record StationKey(String executionPackage, String name) {
    }

    private record TrackKey(String executionPackage, String name) {
    }

    private record ProfileKey(String executionPackage, String track, Integer orderInTrack) {
    }

    private record SectionInsulatorKey(String executionPackage, String station, String name) {
    }

    /** Un poste por su via y su identificador: lo que nombra una fila de DISCONNECTORS. */
    private record PoleKey(String executionPackage, String track, String profileId) {
    }

    /**
     * La via conectada de una fila, o por que no vale. El id es null sin via conectada, y tambien en
     * la simulacion cuando la via se da de alta en esta misma carga.
     */
    private record ConnectedTrack(Long id, String problem) {

        static final ConnectedTrack NONE = new ConnectedTrack(null, null);

        static ConnectedTrack problem(String problem) {
            return new ConnectedTrack(null, problem);
        }
    }

    /** El poste encontrado, o por que no. */
    private record PoleMatch(ProfileMasterRow pole, String problem) {

        static PoleMatch found(ProfileMasterRow pole) {
            return new PoleMatch(pole, null);
        }

        static PoleMatch notFound(String problem) {
            return new PoleMatch(null, problem);
        }
    }
}
