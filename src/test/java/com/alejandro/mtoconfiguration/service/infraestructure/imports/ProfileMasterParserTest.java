package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.core.excel.ExcelException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.DisconnectorMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.TrackMasterRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Comportamiento del lector del maestro de perfiles.
 *
 * <p>El maestro lo genera un script, pero se REVISA a mano antes de importarlo, asi que
 * el error mas probable no es un fallo de codigo sino una hoja mal editada: una columna
 * renombrada, un ENABLED escrito de otra forma, una fila a medias. Estos tests fijan que
 * el parser falle con un mensaje util en el primer caso y que no se invente datos en los
 * otros.
 */
@DisplayName("ProfileMasterParser")
class ProfileMasterParserTest {

    private static final Map<String, String[]> HEADERS = new LinkedHashMap<>();

    static {
        HEADERS.put(ProfileMasterParser.EPS_SHEET, new String[]{
                "EP", "NOMBRE", "INITIAL_PACKAGE", "LENGTH", "START_DATE", "END_DATE",
                "COMPANY_ID_NUMBER", "ENABLED"});
        HEADERS.put(ProfileMasterParser.STATIONS_SHEET, new String[]{"EP", "NOMBRE"});
        HEADERS.put(ProfileMasterParser.TRACKS_SHEET, new String[]{
                "EP", "NOMBRE", "ESTACIONES", "ENABLED", "HOJA_ORIGEN", "FILA_INICIO", "FILA_FIN"});
        HEADERS.put(ProfileMasterParser.PROFILES_SHEET, new String[]{
                "EP", "VIA", "PROFILE_ID", "KP", "PROFILE_STATUS", "SECTIONING", "ANCHORAGE",
                "ANCHORAGE_FOUNDATION", "FOUNDATION", "POLE_TYPE", "PORTAL", "RETURN_SUPPORT",
                "SECTIONING_FEEDING", "SUPPORT_TYPE", "ASSEMBLY_CONFIGURATION",
                "SPAN", "HEIGHT_CANTILEVER_SUPPORT", "POLE_GAUGE_LOCATION",
                "RAIL_POLE_DISTANCE", "ENABLED", "REVISAR", "HOJA_ORIGEN", "FILA_ORIGEN"});
        HEADERS.put(ProfileMasterParser.CANTILEVERS_SHEET, new String[]{
                "EP", "VIA", "PROFILE_ID", "SLOT", "CANTILEVER_TYPE", "STAGGER", "CATENARY_HEIGHT",
                "CW_ELEVATION", "CW_HEIGHT", "WIND_DEFLECTION", "ARM_ANGLE", "STEADY_ARM_TYPE",
                "STEADY_ARM_LENGTH", "ENABLED", "REVISAR", "FILA_ORIGEN"});
    }

    private final ProfileMasterParser parser = new ProfileMasterParser();

    @Nested
    @DisplayName("cuando el maestro esta bien formado")
    class MaestroCorrecto {

        @Test
        @DisplayName("lee el perfil con sus listas de valores y sus campos tecnicos")
        void leeUnPerfilCompleto() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.PROFILES_SHEET, List.<String[]>of(new String[]{
                            "EP6", "TRACK 1 HERZLIYA", "83-1.02", "83063.410", "DEFINITIVE",
                            "", "CP+AnMC", "", "2C3R", "2HEB-240L", "", "RW2", "Disc/IO", "S1", "C.F.21",
                            "52.000", "200", "1475", "-4960", "SI", "NO", "HR Track 1 HER", "8"}))));

            assertThat(content.profiles()).hasSize(1);
            ProfileMasterRow row = content.profiles().getFirst();
            assertThat(row.executionPackage()).isEqualTo("EP6");
            assertThat(row.track()).isEqualTo("TRACK 1 HERZLIYA");
            assertThat(row.profileId()).isEqualTo("83-1.02");
            assertThat(row.kp()).isEqualTo("83063.410");
            assertThat(row.lov().poleType()).isEqualTo("2HEB-240L");
            assertThat(row.lov().sectioningFeeding()).isEqualTo("Disc/IO");
            assertThat(row.lov().supportType())
                    .as("la columna Supports es un campo del perfil desde V20, no una columna sin mapear")
                    .isEqualTo("S1");
            assertThat(row.lov().assemblyConfiguration())
                    .as("la configuracion de montaje es un campo del perfil desde V21")
                    .isEqualTo("C.F.21");
            assertThat(row.span()).isEqualByComparingTo("52.000");
            assertThat(row.railPoleDistance()).isEqualByComparingTo("-4960");
            assertThat(row.enabled()).isTrue();
        }

        @Test
        @DisplayName("lee la mensula con su slot y el brazo ya partido")
        void leeUnaMensula() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.CANTILEVERS_SHEET, List.<String[]>of(new String[]{
                            "EP6", "TRACK 1", "83-1.02", "2", "EMT-2T", "35", "1.400",
                            "0.600", "5.500", "", "6.532", "PH", "1150", "SI", "NO", "8"}))));

            CantileverMasterRow row = content.cantilevers().getFirst();
            assertThat(row.slot()).isEqualTo(2);
            assertThat(row.cantileverType()).isEqualTo("EMT-2T");
            assertThat(row.steadyArmType()).isEqualTo("PH");
            assertThat(row.steadyArmLength()).isEqualTo(1150L);
            assertThat(row.windDeflection()).isNull();
        }

        @Test
        @DisplayName("un brazo sin longitud es normal, no un error")
        void brazoSinLongitud() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.CANTILEVERS_SHEET, List.<String[]>of(new String[]{
                            "EP6", "TRACK 1", "83-1.02", "1", "EMT-1", "", "", "", "", "", "",
                            "PH-Q", "", "SI", "NO", "8"}))));

            CantileverMasterRow row = content.cantilevers().getFirst();
            assertThat(row.steadyArmType()).isEqualTo("PH-Q");
            assertThat(row.steadyArmLength()).isNull();
        }

        @Test
        @DisplayName("una via sin estacion se lee con la estacion en blanco, no falla")
        void viaSinEstacion() throws IOException {
            // TRACK.STATION_ID es anulable a proposito: una via de tramo entre estaciones
            // cuelga directamente del paquete.
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.TRACKS_SHEET, List.<String[]>of(new String[]{
                            "EP6", "TRACK 1 RISHPON-HERZLIYA", "", "SI", "HR Track 1 RIS-HER", "", ""}))));

            TrackMasterRow row = content.tracks().getFirst();
            assertThat(row.stations()).isEmpty();
            assertThat(row.enabled()).isTrue();
        }

        @Test
        @DisplayName("una via larga trae varias estaciones, separadas por barra vertical")
        void viaConVariasEstaciones() throws IOException {
            // Barra y no espacio: 'TLV SAVIDOR' lleva un espacio dentro, asi que partir por
            // espacios habria convertido una estacion en dos.
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.TRACKS_SHEET, List.<String[]>of(new String[]{
                            "EP4", "TRACK 1", "ZIC | BIN | TLV SAVIDOR", "SI", "HR Track 1", "", ""}))));

            assertThat(content.tracks().getFirst().stations())
                    .containsExactly("ZIC", "BIN", "TLV SAVIDOR");
        }

        @Test
        @DisplayName("la fila de origen es la que enseña Excel, para poder ir a la celda")
        void numeroDeFila() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.STATIONS_SHEET, List.of(
                            new String[]{"EP6", "HERZLIYA"},
                            new String[]{"EP6", "TLV SAVIDOR"}))));

            assertThat(content.stations()).extracting("sourceRow").containsExactly(2, 3);
        }

        @Test
        @DisplayName("ENABLED se interpreta con manga ancha: SI, S, X, TRUE o 1")
        void banderaEnabled() throws IOException {
            List<String[]> rows = List.of(
                    new String[]{"EP1", "V1", "", "SI", "", "", ""},
                    new String[]{"EP1", "V2", "", "x", "", "", ""},
                    new String[]{"EP1", "V3", "", "TRUE", "", "", ""},
                    new String[]{"EP1", "V4", "", "1", "", "", ""},
                    new String[]{"EP1", "V5", "", "NO", "", "", ""},
                    new String[]{"EP1", "V6", "", "", "", "", ""});

            ProfileMasterParser.ProfileMasterContent content =
                    parser.parseAll(workbook(Map.of(ProfileMasterParser.TRACKS_SHEET, rows)));

            assertThat(content.tracks()).extracting(TrackMasterRow::enabled)
                    .containsExactly(true, true, true, true, false, false);
        }

        @Test
        @DisplayName("el orden de las columnas no importa: se buscan por nombre")
        void columnasEnOtroOrden() throws IOException {
            Map<String, String[]> headers = new LinkedHashMap<>(HEADERS);
            headers.put(ProfileMasterParser.STATIONS_SHEET, new String[]{"NOMBRE", "EP"});

            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(headers,
                    Map.of(ProfileMasterParser.STATIONS_SHEET,
                            List.<String[]>of(new String[]{"HERZLIYA", "EP6"}))));

            assertThat(content.stations().getFirst().executionPackage()).isEqualTo("EP6");
            assertThat(content.stations().getFirst().name()).isEqualTo("HERZLIYA");
        }
    }

    @Nested
    @DisplayName("cuando el maestro esta mal")
    class MaestroIncorrecto {

        @Test
        @DisplayName("si falta una hoja dice cual y cuales hay")
        void hojaAusente() throws IOException {
            Map<String, String[]> headers = new LinkedHashMap<>(HEADERS);
            headers.remove(ProfileMasterParser.CANTILEVERS_SHEET);

            InputStream excel = workbook(headers, Map.of());

            assertThatThrownBy(() -> parser.parseAll(excel))
                    .isInstanceOf(ExcelException.class)
                    .hasMessageContaining(ProfileMasterParser.CANTILEVERS_SHEET)
                    .hasMessageContaining("Hojas encontradas");
        }

        @Test
        @DisplayName("si falta una columna obligatoria dice cual y cuales hay")
        void columnaObligatoriaAusente() throws IOException {
            // Sin esto el fallo saldria como un maestro que carga la mitad, que es peor.
            Map<String, String[]> headers = new LinkedHashMap<>(HEADERS);
            headers.put(ProfileMasterParser.PROFILES_SHEET, new String[]{"EP", "VIA", "PROFILE_ID"});

            InputStream excel = workbook(headers, Map.of());

            assertThatThrownBy(() -> parser.parseAll(excel))
                    .isInstanceOf(ExcelException.class)
                    .hasMessageContaining("KP")
                    .hasMessageContaining("Columnas encontradas");
        }

        @Test
        @DisplayName("una fila sin clave natural se salta en silencio")
        void filaSinClave() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.PROFILES_SHEET, List.of(
                            new String[]{"EP6", "TRACK 1", "", "10.5"},
                            new String[]{"", "TRACK 1", "83-1.02", "10.5"},
                            new String[]{"EP6", "TRACK 1", "83-1.03", "10.5", "DEFINITIVE"}))));

            assertThat(content.profiles()).extracting(ProfileMasterRow::profileId)
                    .containsExactly("83-1.03");
        }

        @Test
        @DisplayName("una mensula sin slot no se puede colocar y se salta")
        void mensulaSinSlot() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.CANTILEVERS_SHEET, List.<String[]>of(
                            new String[]{"EP6", "TRACK 1", "83-1.02", "", "EMT-1"}))));

            assertThat(content.cantilevers()).isEmpty();
        }

        @Test
        @DisplayName("un numero mal escrito se queda a nulo en vez de tumbar la lectura")
        void numeroIlegible() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of(
                    ProfileMasterParser.PROFILES_SHEET, List.<String[]>of(new String[]{
                            "EP6", "TRACK 1", "83-1.02", "10.5", "DEFINITIVE", "", "", "", "", "",
                            "", "", "", "no soy un numero", "", "", "", "SI"}))));

            assertThat(content.profiles().getFirst().span()).isNull();
        }

        @Test
        @DisplayName("un maestro sin filas no es un error")
        void maestroVacio() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(workbook(Map.of()));

            assertThat(content.executionPackages()).isEmpty();
            assertThat(content.profiles()).isEmpty();
        }
    }

    /**
     * La hoja que llega del libro de revisión del plano de seccionamiento. Es opcional, como las de
     * los aisladores: un maestro sin ella se lee sin seccionadores.
     */
    @Nested
    @DisplayName("la hoja DISCONNECTORS")
    class HojaDeSeccionadores {

        private static final String[] HEADER = {"EP", "ESTACION", "VIA", "VIA_CONECTADA", "PROFILE_ID", "KP_POSTE",
                "NOMBRE", "KP", "ON_LOAD", "NORMALLY_OPEN", "DRIVE_TYPE", "DISCONNECTOR_FUNCTION", "ENABLED",
                "REVISAR"};

        @Test
        @DisplayName("lee el seccionador con su poste o con su KP propio, y los SI/NO tal cual vienen")
        void leeLosSeccionadores() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(withDisconnectors(HEADER, List.of(
                    new String[]{"EP9A", "LOD", "TRACK 1", "", "5-1.03", "5000.410", "LOD-03", "", "SI", "NO",
                            "MOTOR", "Disc/IO", "SI", "NO"},
                    new String[]{"EP4", "BIN", "TRACK 2 BIN", "TRACK 1", "", "", "BIN-B01", "98375.5", "NO", "", "",
                            "Disc/PP", "NO", "SI"})));

            assertThat(content.disconnectors()).hasSize(2);
            DisconnectorMasterRow enPoste = content.disconnectors().getFirst();
            assertThat(enPoste.connectedTrack()).isEmpty();
            assertThat(enPoste.profileId()).isEqualTo("5-1.03");
            assertThat(enPoste.profileKp()).isEqualByComparingTo("5000.41");
            assertThat(enPoste.kp()).isEmpty();
            assertThat(enPoste.onLoad()).isEqualTo("SI");
            assertThat(enPoste.normallyOpen()).isEqualTo("NO");
            assertThat(enPoste.driveType()).isEqualTo("MOTOR");
            assertThat(enPoste.disconnectorFunction()).isEqualTo("Disc/IO");
            assertThat(enPoste.enabled()).isTrue();
            assertThat(enPoste.sourceRow()).isEqualTo(2);

            DisconnectorMasterRow sinPoste = content.disconnectors().get(1);
            assertThat(sinPoste.station()).isEqualTo("BIN");
            assertThat(sinPoste.track()).isEqualTo("TRACK 2 BIN");
            assertThat(sinPoste.connectedTrack()).isEqualTo("TRACK 1");
            assertThat(sinPoste.profileId()).isEmpty();
            assertThat(sinPoste.profileKp()).isNull();
            assertThat(sinPoste.kp()).isEqualTo("98375.5");
            assertThat(sinPoste.enabled()).isFalse();
        }

        @Test
        @DisplayName("un maestro sin la hoja se lee sin seccionadores")
        void sinLaHoja() throws IOException {
            assertThat(parser.parseAll(workbook(Map.of())).disconnectors()).isEmpty();
        }

        /**
         * Sin la columna PROFILE_ID, todos los seccionadores se cargarian sin poste y sin una sola
         * queja: la cabecera se exige aunque la celda pueda ir vacia.
         */
        @Test
        @DisplayName("si la hoja esta, PROFILE_ID es obligatoria en la cabecera aunque su celda pueda ir vacia")
        void profileIdObligatoria() throws IOException {
            InputStream excel = withDisconnectors(
                    new String[]{"EP", "ESTACION", "VIA", "NOMBRE", "ON_LOAD", "DISCONNECTOR_FUNCTION", "ENABLED"},
                    List.of());

            assertThatThrownBy(() -> parser.parseAll(excel))
                    .isInstanceOf(ExcelException.class)
                    .hasMessageContaining("PROFILE_ID")
                    .hasMessageContaining(ProfileMasterParser.DISCONNECTORS_SHEET);
        }

        /**
         * KP y KP_POSTE llegaron con V26, el estado y el accionamiento con V25 y la via conectada con
         * V27: son opcionales.
         */
        @Test
        @DisplayName("KP, KP_POSTE, VIA_CONECTADA, NORMALLY_OPEN y DRIVE_TYPE pueden faltar en la cabecera")
        void columnasOpcionales() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(withDisconnectors(
                    new String[]{"EP", "ESTACION", "VIA", "PROFILE_ID", "NOMBRE", "ON_LOAD", "DISCONNECTOR_FUNCTION",
                            "ENABLED"},
                    List.<String[]>of(new String[]{"EP6", "HER", "TRACK 1", "83-1.02", "HER-01", "SI", "Disc/IO", "SI"})));

            DisconnectorMasterRow row = content.disconnectors().getFirst();
            assertThat(row.kp()).isEmpty();
            assertThat(row.profileKp()).isNull();
            assertThat(row.connectedTrack()).isEmpty();
            assertThat(row.normallyOpen()).isEmpty();
            assertThat(row.driveType()).isEmpty();
        }

        @Test
        @DisplayName("una fila sin paquete o sin nombre se salta, como en las demas hojas")
        void filaSinClave() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(withDisconnectors(HEADER, List.of(
                    new String[]{"EP6", "HER", "TRACK 1", "", "", "", ""},
                    new String[]{"", "HER", "TRACK 1", "", "", "", "HER-02"},
                    new String[]{"EP6", "HER", "TRACK 1", "", "", "", "HER-03"})));

            assertThat(content.disconnectors()).extracting(DisconnectorMasterRow::name).containsExactly("HER-03");
        }

        /**
         * La estacion es opcional y se dice con SIN ESTACION: una celda vacia es un olvido, y saltar la
         * fila la perderia sin decir nada. Llega al importador, que la señala en su fila.
         */
        @Test
        @DisplayName("una fila sin estacion se lee, para que el importador la señale, y SIN ESTACION llega tal cual")
        void filaSinEstacion() throws IOException {
            ProfileMasterParser.ProfileMasterContent content = parser.parseAll(withDisconnectors(HEADER, List.of(
                    new String[]{"EP6", "", "TRACK 1", "", "", "", "HER-02"},
                    new String[]{"EP6", "SIN ESTACION", "TRACK 1", "", "", "", "HER-03"})));

            assertThat(content.disconnectors())
                    .extracting(DisconnectorMasterRow::name, DisconnectorMasterRow::station)
                    .containsExactly(tuple("HER-02", ""), tuple("HER-03", "SIN ESTACION"));
        }

        private InputStream withDisconnectors(String[] header, List<String[]> rows) throws IOException {
            Map<String, String[]> headers = new LinkedHashMap<>(HEADERS);
            headers.put(ProfileMasterParser.DISCONNECTORS_SHEET, header);
            return workbook(headers, Map.of(ProfileMasterParser.DISCONNECTORS_SHEET, rows));
        }
    }

    private InputStream workbook(Map<String, List<String[]>> rowsBySheet) throws IOException {
        return workbook(HEADERS, rowsBySheet);
    }

    private InputStream workbook(Map<String, String[]> headers,
                                 Map<String, List<String[]>> rowsBySheet) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (Map.Entry<String, String[]> entry : headers.entrySet()) {
                Sheet sheet = workbook.createSheet(entry.getKey());
                writeRow(sheet.createRow(0), entry.getValue());

                List<String[]> rows = rowsBySheet.getOrDefault(entry.getKey(), List.of());
                for (int index = 0; index < rows.size(); index++) {
                    writeRow(sheet.createRow(index + 1), rows.get(index));
                }
            }
            workbook.write(out);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void writeRow(Row row, String[] values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
