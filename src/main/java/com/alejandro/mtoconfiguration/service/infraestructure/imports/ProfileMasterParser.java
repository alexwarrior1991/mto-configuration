package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.core.excel.ExcelCell;
import com.alejandro.mtoconfiguration.core.excel.ExcelException;
import com.alejandro.mtoconfiguration.core.excel.ExcelReader;
import com.alejandro.mtoconfiguration.core.excel.ExcelRow;
import com.alejandro.mtoconfiguration.core.excel.ExcelSheet;
import com.alejandro.mtoconfiguration.core.excel.ExcelWorkbook;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileLovCodes;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.StationMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.TrackMasterRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lee {@code profile-master.xlsx}, el maestro de infraestructura.
 *
 * <p>Mismo criterio que {@code LovMasterParser}: el maestro tiene formato fijo y lo
 * genera un script, asi que este parser puede ser estricto. Si falta una hoja o una
 * columna obligatoria falla diciendo <b>cual</b> y <b>que si encontro</b>, en lugar de
 * seguir y cargar datos a medias. Las columnas se localizan por nombre, para que anadir
 * una columna informativa al maestro no rompa la importacion.
 *
 * <p>Las cinco hojas se leen de una sola pasada porque {@link ExcelReader} consume el
 * InputStream: recorrerlo cinco veces obligaria a rebobinarlo o a duplicarlo en memoria,
 * y el maestro son 2,7 MB.
 */
@Component
public class ProfileMasterParser {

    public static final String EPS_SHEET = "EPS";
    public static final String STATIONS_SHEET = "STATIONS";
    public static final String TRACKS_SHEET = "TRACKS";
    public static final String PROFILES_SHEET = "PROFILES";
    public static final String CANTILEVERS_SHEET = "CANTILEVERS";

    private static final String COL_EP = "EP";
    private static final String COL_NAME = "NOMBRE";
    private static final String COL_TRACK = "VIA";
    private static final String COL_STATION = "ESTACION";
    private static final String COL_PROFILE_ID = "PROFILE_ID";
    private static final String COL_ENABLED = "ENABLED";
    private static final String COL_SLOT = "SLOT";

    private static final int HEADER_ROW = 0;
    private static final int FIRST_DATA_ROW = 1;

    private final ExcelReader excelReader = new ExcelReader();

    /** Las cinco hojas de datos del maestro. El InputStream se cierra siempre. */
    public ProfileMasterContent parseAll(InputStream inputStream) {
        ExcelWorkbook workbook = excelReader.read(inputStream);
        return new ProfileMasterContent(
                readExecutionPackages(workbook),
                readStations(workbook),
                readTracks(workbook),
                readProfiles(workbook),
                readCantilevers(workbook));
    }

    private List<ExecutionPackageMasterRow> readExecutionPackages(ExcelWorkbook workbook) {
        return read(workbook, EPS_SHEET, List.of(COL_EP, COL_NAME, COL_ENABLED), (row, columns, index) -> {
            String code = text(row, columns.get(COL_EP));
            String name = text(row, columns.get(COL_NAME));
            if (code.isBlank() || name.isBlank()) {
                return null;
            }
            return new ExecutionPackageMasterRow(code, name,
                    flag(text(row, columns.get("INITIAL_PACKAGE"))),
                    longer(row, columns.get("LENGTH")),
                    date(row, columns.get("START_DATE")),
                    date(row, columns.get("END_DATE")),
                    text(row, columns.get("COMPANY_ID_NUMBER")),
                    flag(text(row, columns.get(COL_ENABLED))),
                    index + 1);
        });
    }

    private List<StationMasterRow> readStations(ExcelWorkbook workbook) {
        return read(workbook, STATIONS_SHEET, List.of(COL_EP, COL_NAME), (row, columns, index) -> {
            String ep = text(row, columns.get(COL_EP));
            String name = text(row, columns.get(COL_NAME));
            return ep.isBlank() || name.isBlank() ? null : new StationMasterRow(ep, name, index + 1);
        });
    }

    private List<TrackMasterRow> readTracks(ExcelWorkbook workbook) {
        return read(workbook, TRACKS_SHEET, List.of(COL_EP, COL_NAME, COL_ENABLED), (row, columns, index) -> {
            String ep = text(row, columns.get(COL_EP));
            String name = text(row, columns.get(COL_NAME));
            if (ep.isBlank() || name.isBlank()) {
                return null;
            }
            return new TrackMasterRow(ep, name, text(row, columns.get(COL_STATION)),
                    flag(text(row, columns.get(COL_ENABLED))), index + 1);
        });
    }

    private List<ProfileMasterRow> readProfiles(ExcelWorkbook workbook) {
        return read(workbook, PROFILES_SHEET,
                List.of(COL_EP, COL_TRACK, COL_PROFILE_ID, "KP", COL_ENABLED),
                (row, columns, index) -> {
                    String ep = text(row, columns.get(COL_EP));
                    String track = text(row, columns.get(COL_TRACK));
                    String profileId = text(row, columns.get(COL_PROFILE_ID));
                    if (ep.isBlank() || track.isBlank() || profileId.isBlank()) {
                        return null;
                    }
                    ProfileLovCodes lov = new ProfileLovCodes(
                            text(row, columns.get("SECTIONING")),
                            text(row, columns.get("ANCHORAGE")),
                            text(row, columns.get("ANCHORAGE_FOUNDATION")),
                            text(row, columns.get("FOUNDATION")),
                            text(row, columns.get("POLE_TYPE")),
                            text(row, columns.get("PORTAL")),
                            text(row, columns.get("RETURN_SUPPORT")),
                            text(row, columns.get("SECTIONING_FEEDING")));
                    return new ProfileMasterRow(ep, track, profileId,
                            text(row, columns.get("KP")),
                            text(row, columns.get("PROFILE_STATUS")),
                            lov,
                            decimal(row, columns.get("SPAN")),
                            decimal(row, columns.get("HEIGHT_CANTILEVER_SUPPORT")),
                            decimal(row, columns.get("POLE_GAUGE_LOCATION")),
                            decimal(row, columns.get("RAIL_POLE_DISTANCE")),
                            flag(text(row, columns.get(COL_ENABLED))),
                            index + 1);
                });
    }

    private List<CantileverMasterRow> readCantilevers(ExcelWorkbook workbook) {
        return read(workbook, CANTILEVERS_SHEET,
                List.of(COL_EP, COL_TRACK, COL_PROFILE_ID, COL_SLOT, COL_ENABLED),
                (row, columns, index) -> {
                    String ep = text(row, columns.get(COL_EP));
                    String track = text(row, columns.get(COL_TRACK));
                    String profileId = text(row, columns.get(COL_PROFILE_ID));
                    Long slot = longer(row, columns.get(COL_SLOT));
                    if (ep.isBlank() || track.isBlank() || profileId.isBlank() || slot == null) {
                        return null;
                    }
                    return new CantileverMasterRow(ep, track, profileId, slot.intValue(),
                            text(row, columns.get("CANTILEVER_TYPE")),
                            decimal(row, columns.get("STAGGER")),
                            decimal(row, columns.get("CATENARY_HEIGHT")),
                            decimal(row, columns.get("CW_ELEVATION")),
                            decimal(row, columns.get("CW_HEIGHT")),
                            decimal(row, columns.get("WIND_DEFLECTION")),
                            decimal(row, columns.get("ARM_ANGLE")),
                            text(row, columns.get("STEADY_ARM_TYPE")),
                            longer(row, columns.get("STEADY_ARM_LENGTH")),
                            flag(text(row, columns.get(COL_ENABLED))),
                            index + 1);
                });
    }

    private <T> List<T> read(ExcelWorkbook workbook, String sheetName, List<String> required,
                             RowReader<T> reader) {
        ExcelSheet sheet = workbook.sheet(sheetName)
                .orElseThrow(() -> new ExcelException(
                        "El fichero no tiene la hoja '" + sheetName + "'. Hojas encontradas: "
                                + workbook.sheetNames()));

        // La cabecera se valida ANTES de mirar si hay filas, y no despues: una hoja sin
        // datos y con una columna renombrada importaria cero filas sin decir nada, que es
        // justo el fallo silencioso que este parser existe para evitar. La hoja STATIONS
        // llega vacia de forma legitima mientras nadie declare estaciones, y su cabecera
        // sigue teniendo que estar bien.
        Map<String, Integer> columns = readHeader(sheet, sheetName, required);

        if (sheet.rowCount() <= FIRST_DATA_ROW) {
            return List.of();
        }

        List<T> rows = new ArrayList<>();
        for (int index = FIRST_DATA_ROW; index < sheet.rowCount(); index++) {
            ExcelRow row = sheet.row(index);
            if (row.isEmpty()) {
                continue;
            }
            T parsed = reader.read(row, columns, index);
            if (parsed != null) {
                rows.add(parsed);
            }
        }
        return rows;
    }

    private Map<String, Integer> readHeader(ExcelSheet sheet, String sheetName, List<String> required) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (ExcelCell cell : sheet.row(HEADER_ROW).cells()) {
            String name = cell.asString().map(value -> value.trim().toUpperCase(Locale.ROOT)).orElse("");
            if (!name.isBlank()) {
                columns.putIfAbsent(name, cell.columnIndex());
            }
        }

        for (String column : required) {
            if (!columns.containsKey(column)) {
                throw new ExcelException("La hoja '" + sheetName + "' no tiene la columna obligatoria '"
                        + column + "'. Columnas encontradas: " + columns.keySet());
            }
        }
        return columns;
    }

    /**
     * ENABLED decide si la fila se carga, asi que se interpreta con manga ancha: quien
     * revisa el maestro escribe indistintamente SI, S, X, TRUE o 1. Cualquier otra cosa,
     * incluida la celda vacia, cuenta como NO.
     */
    private boolean flag(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return value.equals("SI") || value.equals("SÍ") || value.equals("S")
                || value.equals("X") || value.equals("TRUE") || value.equals("1");
    }

    private String text(ExcelRow row, Integer column) {
        if (column == null) {
            return "";
        }
        ExcelCell cell = row.cell(column);
        return cell.asString()
                .or(() -> cell.asNumber().map(this::plainNumber))
                .orElse("")
                .trim();
    }

    private BigDecimal decimal(ExcelRow row, Integer column) {
        if (column == null) {
            return null;
        }
        ExcelCell cell = row.cell(column);
        // Un numero escrito por openpyxl llega como Double; uno escrito como texto, no.
        // Se pasa por String en los dos casos para no arrastrar el ruido del binario:
        // new BigDecimal(1475.0d) da 1475.00000000000000000000...
        Optional<Double> numeric = cell.asNumber();
        if (numeric.isPresent()) {
            return new BigDecimal(plainNumber(numeric.get()));
        }
        String value = cell.asString().orElse("").trim();
        try {
            return value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longer(ExcelRow row, Integer column) {
        BigDecimal value = decimal(row, column);
        return value == null ? null : value.longValue();
    }

    private LocalDate date(ExcelRow row, Integer column) {
        if (column == null) {
            return null;
        }
        ExcelCell cell = row.cell(column);
        Optional<LocalDateTime> stamp = cell.asDate();
        if (stamp.isPresent()) {
            return stamp.get().toLocalDate();
        }
        String value = cell.asString().orElse("").trim();
        try {
            return value.isBlank() ? null : LocalDate.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String plainNumber(Double value) {
        return value == Math.rint(value) && !value.isInfinite()
                ? String.valueOf(value.longValue())
                : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(ExcelRow row, Map<String, Integer> columns, int index);
    }

    /** Las cinco hojas de datos, leidas de una vez. */
    public record ProfileMasterContent(
            List<ExecutionPackageMasterRow> executionPackages,
            List<StationMasterRow> stations,
            List<TrackMasterRow> tracks,
            List<ProfileMasterRow> profiles,
            List<CantileverMasterRow> cantilevers
    ) {
    }
}
