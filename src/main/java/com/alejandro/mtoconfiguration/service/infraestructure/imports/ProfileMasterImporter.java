package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.GenericException;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.StationMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.TrackMasterRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Carga {@code profile-master.xlsx} en base de datos.
 *
 * <h2>El orden no es negociable</h2>
 *
 * <p>{@code EPS -> STATIONS -> TRACKS -> PROFILES (+ CANTILEVERS)}. Cada nivel necesita
 * el identificador del anterior, que solo se conoce despues de escribirlo. Una vía sin
 * estación es correcta —la columna es anulable a proposito— pero una vía sin paquete no.
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
        importProfiles(content, tracksByKey, dryRun, report, progress);

        log.info("Importacion del maestro de perfiles terminada dryRun={} altas={} modificaciones={} "
                        + "mensulas={} errores={} omitidos={}",
                dryRun, report.getCreated(), report.getUpdated(), report.getCantileversWritten(),
                report.getFailed(), report.getSkippedDisabled());
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
                count(report, ProfileImportReport.EXECUTION_PACKAGE, result.created());
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
                count(report, ProfileImportReport.STATION, result.created());
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

            // Una via sin estacion es correcta; una que declara una estacion que no existe, no.
            Long stationId = null;
            if (!row.station().isBlank()) {
                stationId = stationsByKey.get(new StationKey(code, key(row.station())));
                if (stationId == null && !dryRun) {
                    fail(report, row.sourceRow(), ProfileImportReport.TRACK, reference(row),
                            "declara la estacion '" + row.station() + "', que no esta en la hoja STATIONS",
                            progress);
                    continue;
                }
            }

            try {
                var result = upsertService.upsertTrack(row, packagesByCode.get(code), stationId, dryRun);
                count(report, ProfileImportReport.TRACK, result.created());
                byKey.put(new TrackKey(code, key(row.name())), result.id());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.TRACK, reference(row), e, progress);
            }
        }
        return byKey;
    }

    private void importProfiles(ProfileMasterParser.ProfileMasterContent content,
                                Map<TrackKey, Long> tracksByKey, boolean dryRun,
                                ProfileImportReport report, Consumer<Boolean> progress) {
        // Las mensulas se agrupan de una vez: buscarlas por perfil dentro del bucle seria
        // recorrer 14.592 filas por cada uno de los 11.715 perfiles.
        Map<ProfileKey, List<CantileverMasterRow>> cantileversByProfile = content.cantilevers().stream()
                .filter(CantileverMasterRow::enabled)
                .collect(Collectors.groupingBy(row -> new ProfileKey(
                        key(row.executionPackage()), key(row.track()), key(row.profileId()))));

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

            List<CantileverMasterRow> cantilevers = cantileversByProfile.getOrDefault(
                    new ProfileKey(trackKey.executionPackage(), trackKey.name(), key(row.profileId())),
                    List.of());

            try {
                var result = upsertService.upsertProfile(row, tracksByKey.get(trackKey), cantilevers, dryRun);
                count(report, ProfileImportReport.PROFILE, result.created());
                report.addCantilevers(cantilevers.size());
                progress.accept(true);
            } catch (Exception e) {
                fail(report, row.sourceRow(), ProfileImportReport.PROFILE, reference(row), e, progress);
            }
        }
    }

    private void count(ProfileImportReport report, String entity, boolean created) {
        if (created) {
            report.outcomeOf(entity).create();
        } else {
            report.outcomeOf(entity).update();
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

    private record ProfileKey(String executionPackage, String track, String profileId) {
    }
}
