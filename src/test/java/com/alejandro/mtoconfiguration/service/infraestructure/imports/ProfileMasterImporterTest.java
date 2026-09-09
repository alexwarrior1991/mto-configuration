package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileLovCodes;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.StationMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.TrackMasterRow;
import com.alejandro.mtoconfiguration.service.infraestructure.imports.InfrastructureUpsertService.UpsertResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orquestacion de la importacion del maestro de perfiles.
 *
 * <p>Lo que se prueba aqui no es que escriba —de eso se encarga
 * {@code InfrastructureUpsertService}— sino el ORDEN y lo que pasa cuando algo falla a
 * mitad: un nivel necesita el identificador del anterior, y sin cuidado un paquete
 * fallido se convierte en mil violaciones de clave ajena que no dicen nada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileMasterImporterTest {

    @Mock
    private ProfileMasterParser parser;
    @Mock
    private InfrastructureUpsertService upsertService;

    private ProfileMasterImporter importer;

    private static final InputStream ANY_FILE = new ByteArrayInputStream(new byte[]{1});

    @BeforeEach
    void setUp() {
        importer = new ProfileMasterImporter(parser, upsertService);
        when(upsertService.upsertExecutionPackage(any(), anyBoolean()))
                .thenReturn(new UpsertResult(1L, true));
        when(upsertService.upsertStation(any(), anyLong(), anyBoolean()))
                .thenReturn(new UpsertResult(2L, true));
        when(upsertService.upsertTrack(any(), anyLong(), any(), anyBoolean()))
                .thenReturn(new UpsertResult(3L, true));
        when(upsertService.upsertProfile(any(), anyLong(), any(), anyBoolean()))
                .thenReturn(new UpsertResult(4L, true));
    }

    @Test
    @DisplayName("carga el arbol entero y cuenta cada nivel por separado")
    void cargaCompleta() {
        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")),
                List.of(profile("EP6", "TRACK 1", "83-1.02")),
                List.of(cantilever("EP6", "TRACK 1", "83-1.02", 1)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.outcomeOf(ProfileImportReport.EXECUTION_PACKAGE).getCreated()).isEqualTo(1);
        assertThat(report.outcomeOf(ProfileImportReport.STATION).getCreated()).isEqualTo(1);
        assertThat(report.outcomeOf(ProfileImportReport.TRACK).getCreated()).isEqualTo(1);
        assertThat(report.outcomeOf(ProfileImportReport.PROFILE).getCreated()).isEqualTo(1);
        assertThat(report.getCantileversWritten()).isEqualTo(1);
        assertThat(report.getFailed()).isZero();
    }

    @Test
    @DisplayName("una via sin estacion cuelga del paquete, que es una declaracion valida")
    void viaSinEstacion() {
        givenMaster(List.of(ep("EP6")), List.of(),
                List.of(track("EP6", "TRACK 1 RIS-HER")),
                List.of(), List.of());

        importer.importFrom(ANY_FILE, false);

        verify(upsertService).upsertTrack(any(), eq(1L), eq(List.of()), eq(false));
    }

    @Test
    @DisplayName("una via larga se liga a TODAS las estaciones que atraviesa, no a la primera")
    void viaConVariasEstaciones() {
        // 'TRACK 1' de EP4 pasa por ZIC, por BIN y por HAD sin dejar de ser una via. Quedarse
        // con la primera era lo que hacia el modelo anterior, y perdia las otras dos.
        // Un id distinto por estacion: con el stub por defecto las tres devolverian el mismo y
        // el test pasaria aunque el importador se quedara con una sola.
        AtomicLong siguiente = new AtomicLong(10);
        when(upsertService.upsertStation(any(), anyLong(), anyBoolean()))
                .thenAnswer(invocation -> new UpsertResult(siguiente.getAndIncrement(), true));

        givenMaster(List.of(ep("EP4")),
                List.of(station("EP4", "ZIC"), station("EP4", "BIN"), station("EP4", "HAD")),
                List.of(track("EP4", "TRACK 1", "ZIC", "BIN", "HAD")),
                List.of(), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        verify(upsertService).upsertTrack(any(), eq(1L), eq(List.of(10L, 11L, 12L)), eq(false));
        assertThat(report.getFailed()).isZero();
    }

    @Test
    @DisplayName("una via que declara una estacion inexistente no se carga a medias")
    void viaConEstacionDesconocida() {
        givenMaster(List.of(ep("EP6")), List.of(),
                List.of(track("EP6", "TRACK 1", "NO EXISTE")),
                List.of(), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        verify(upsertService, never()).upsertTrack(any(), anyLong(), any(), anyBoolean());
        assertThat(report.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("NO EXISTE"));
    }

    @Test
    @DisplayName("si falla el paquete, sus hijos se cuentan una vez y con el motivo real")
    void paqueteFallido() {
        // Lo contrario —dejarlos intentarlo— daria mil violaciones de clave ajena que solo
        // dicen que algo fue mal mas arriba.
        when(upsertService.upsertExecutionPackage(any(), anyBoolean()))
                .thenThrow(new ValidationException(List.of(Alert.ofDanger("VAL-001", "companyId"))));

        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "")),
                List.of(profile("EP6", "TRACK 1", "83-1.02")), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        verify(upsertService, never()).upsertStation(any(), anyLong(), anyBoolean());
        verify(upsertService, never()).upsertTrack(any(), anyLong(), any(), anyBoolean());
        verify(upsertService, never()).upsertProfile(any(), anyLong(), any(), anyBoolean());
        assertThat(report.getErrors()).hasSize(4);
        assertThat(report.getErrors().get(1).message()).contains("EP6").contains("no se ha podido cargar");
    }

    /**
     * El motivo tiene que llegar ENTERO al informe. {@code messageOf} no usa
     * {@code getMessage()} cuando la excepcion trae alertas, y las de un solo mensaje las
     * construye {@code BaseException} por su cuenta: si esa ruta perdiera el texto, quien
     * lee el informe se quedaria con un codigo y sin saber que NIF esta mal.
     */
    @Test
    @DisplayName("el motivo de un NIF sin empresa llega al informe con el NIF dentro")
    void elMotivoDelNifLlegaEntero() {
        when(upsertService.upsertExecutionPackage(any(), anyBoolean()))
                .thenThrow(new NotFoundException(
                        "no existe ninguna empresa con NIF 'B12345678' en business_entity"));

        givenMaster(List.of(ep("EP6")), List.of(), List.of(), List.of(), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getErrors()).hasSize(1);
        assertThat(report.getErrors().getFirst().message())
                .contains("B12345678")
                .contains("business_entity");
    }

    @Test
    @DisplayName("el error de una fila lleva su numero de fila y su clave natural")
    void errorConReferencia() {
        when(upsertService.upsertProfile(any(), anyLong(), any(), anyBoolean()))
                .thenThrow(new ValidationException(List.of(Alert.ofDanger("VAL-001", "kp"))));

        givenMaster(List.of(ep("EP6")), List.of(), List.of(track("EP6", "TRACK 1", "")),
                List.of(profile("EP6", "TRACK 1", "83-1.02")), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.row()).isEqualTo(7);
            assertThat(error.reference()).isEqualTo("EP6 / TRACK 1 / 83-1.02");
            assertThat(error.message()).contains("VAL-001");
        });
    }

    @Test
    @DisplayName("una fila mala no envenena a las siguientes")
    void unaFilaMalaNoDetieneElResto() {
        when(upsertService.upsertProfile(any(), anyLong(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(new UpsertResult(9L, true));

        givenMaster(List.of(ep("EP6")), List.of(), List.of(track("EP6", "TRACK 1", "")),
                List.of(profile("EP6", "TRACK 1", "A"), profile("EP6", "TRACK 1", "B")), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getFailed()).isEqualTo(1);
        assertThat(report.outcomeOf(ProfileImportReport.PROFILE).getCreated()).isEqualTo(1);
    }

    @Test
    @DisplayName("las filas con ENABLED=NO se saltan y se cuentan")
    void filasDeshabilitadas() {
        ProfileMasterRow disabled = new ProfileMasterRow("EP6", "TRACK 1", "83-1.02", "10.5",
                1, "DEFINITIVE", ProfileLovCodes.empty(), null, null, null, null, false, 7);

        givenMaster(List.of(ep("EP6")), List.of(), List.of(track("EP6", "TRACK 1", "")),
                List.of(disabled), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        verify(upsertService, never()).upsertProfile(any(), anyLong(), any(), anyBoolean());
        assertThat(report.getSkippedDisabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("cada perfil recibe SOLO sus mensulas")
    void mensulasPorPerfil() {
        givenMaster(List.of(ep("EP6")), List.of(), List.of(track("EP6", "TRACK 1", "")),
                List.of(profile("EP6", "TRACK 1", "A"), profile("EP6", "TRACK 1", "B")),
                List.of(cantilever("EP6", "TRACK 1", "A", 1),
                        cantilever("EP6", "TRACK 1", "A", 2),
                        cantilever("EP6", "TRACK 1", "B", 1)));

        importer.importFrom(ANY_FILE, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CantileverMasterRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(upsertService, org.mockito.Mockito.times(2))
                .upsertProfile(any(), anyLong(), captor.capture(), anyBoolean());

        assertThat(captor.getAllValues().get(0)).hasSize(2);
        assertThat(captor.getAllValues().get(1)).hasSize(1);
    }

    @Test
    @DisplayName("la simulacion recorre lo mismo y produce los mismos recuentos")
    void simulacion() {
        // Es la unica propiedad que hace util un dryRun: poder comparar lo que dijo con
        // lo que despues hace la carga real.
        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")),
                List.of(profile("EP6", "TRACK 1", "83-1.02")),
                List.of(cantilever("EP6", "TRACK 1", "83-1.02", 1)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.getCreated()).isEqualTo(4);
        assertThat(report.getCantileversWritten()).isEqualTo(1);
        verify(upsertService).upsertProfile(any(), anyLong(), any(), eq(true));
    }

    @Test
    @DisplayName("las claves se cruzan ignorando mayusculas, igual que los indices de V12")
    void clavesInsensiblesAMayusculas() {
        // El origen no es consistente: 'HR TRACK 3 HAD' convive con 'HR Track 3 BIN'.
        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "herzliya")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")),
                List.of(profile("ep6", "track 1", "83-1.02")), List.of());

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getFailed()).isZero();
        verify(upsertService).upsertProfile(any(), eq(3L), any(), anyBoolean());
    }

    private void givenMaster(List<ExecutionPackageMasterRow> eps, List<StationMasterRow> stations,
                             List<TrackMasterRow> tracks, List<ProfileMasterRow> profiles,
                             List<CantileverMasterRow> cantilevers) {
        when(parser.parseAll(any())).thenReturn(new ProfileMasterParser.ProfileMasterContent(
                new ArrayList<>(eps), new ArrayList<>(stations), new ArrayList<>(tracks),
                new ArrayList<>(profiles), new ArrayList<>(cantilevers)));
    }

    private static ExecutionPackageMasterRow ep(String code) {
        return new ExecutionPackageMasterRow(code, code, false, 1000L,
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 1, 1), "B1", true, 2);
    }

    private static StationMasterRow station(String ep, String name) {
        return new StationMasterRow(ep, name, 3);
    }

    /** Sin estaciones -> {@code track(ep, name)}; la cadena vacia tambien vale por comodidad. */
    private static TrackMasterRow track(String ep, String name, String... stations) {
        return new TrackMasterRow(ep, name,
                Arrays.stream(stations).filter(each -> !each.isBlank()).toList(), true, 5);
    }

    private static ProfileMasterRow profile(String ep, String track, String profileId) {
        return new ProfileMasterRow(ep, track, profileId, "83063.410", 1, "DEFINITIVE",
                ProfileLovCodes.empty(), new BigDecimal("52.000"), null, null, null, true, 7);
    }

    private static CantileverMasterRow cantilever(String ep, String track, String profileId, int slot) {
        return new CantileverMasterRow(ep, track, profileId, slot, "EMT-1", null, null, null,
                null, null, null, "PH", 1150L, true, 9);
    }
}
