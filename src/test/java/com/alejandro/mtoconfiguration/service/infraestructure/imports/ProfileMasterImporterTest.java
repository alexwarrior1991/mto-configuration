package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileLovCodes;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.SectionInsulatorMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.SectionInsulatorSwitchMasterRow;
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
import static org.assertj.core.api.Assertions.tuple;
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
                .thenReturn(UpsertResult.created(1L));
        when(upsertService.upsertStation(any(), anyLong(), anyBoolean()))
                .thenReturn(UpsertResult.created(2L));
        when(upsertService.upsertTrack(any(), anyLong(), any(), anyBoolean()))
                .thenReturn(UpsertResult.created(3L));
        when(upsertService.upsertProfile(any(), anyLong(), any(), anyBoolean()))
                .thenReturn(UpsertResult.created(4L));
        when(upsertService.upsertSectionInsulator(any(), anyLong(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(UpsertResult.created(5L));
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

    /**
     * Lo mismo que con la aguja huerfana, en la mensula.
     *
     * <p>Se empareja por ORDEN y no por identificador de perfil, asi que una fila con el orden
     * equivocado se quedaba fuera en silencio y el perfil salia con una mensula de menos que nadie
     * iba a echar en falta hasta mirar el poste.
     */
    @Test
    @DisplayName("una mensula cuyo perfil no existe sale en el informe, no se pierde")
    void laMensulaHuerfanaSaleEnElInforme() {
        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")),
                List.of(profile("EP6", "TRACK 1", "83-1.02", 1)),
                List.of(cantilever("EP6", "TRACK 1", "83-1.02", 1, 1),
                        // Orden 7: en esa via no hay perfil con ese orden.
                        cantilever("EP6", "TRACK 1", "83-1.02", 7, 2)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.entity()).isEqualTo(ProfileImportReport.PROFILE);
                    assertThat(error.reference()).contains("83-1.02", "SLOT 2");
                    assertThat(error.message()).contains("PROFILES", "ORDEN 7", "TRACK 1");
                });
        // La que si casa se importa igual.
        assertThat(report.getCantileversWritten()).isEqualTo(1);
    }

    /** Un perfil deshabilitado ya tiene su linea (omitido): sus mensulas no son huerfanas. */
    @Test
    @DisplayName("las mensulas de un perfil deshabilitado no se cuentan como huerfanas")
    void lasMensulasDeUnPerfilDeshabilitadoNoSonHuerfanas() {
        ProfileMasterRow deshabilitado = new ProfileMasterRow("EP6", "TRACK 1", "83-1.02",
                "83063.410", 1, "DEFINITIVE", ProfileLovCodes.empty(), new BigDecimal("52.000"),
                null, null, null, false, 7);

        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")),
                List.of(deshabilitado),
                List.of(cantilever("EP6", "TRACK 1", "83-1.02", 1, 1)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getSkippedDisabled()).isEqualTo(1);
    }

    /**
     * Una errata en AISLADOR no puede perder la aguja en silencio.
     *
     * <p>Antes no casaba con ningun aislador, nadie la consumia y el informe salia limpio: el dia
     * de la carga faltaria una aguja sin nada que dijera por donde buscar. Ahora sale como error
     * con su fila de origen y con el nombre que no se encontro.
     */
    @Test
    @DisplayName("una aguja cuyo aislador no existe sale en el informe, no se pierde")
    void laAgujaHuerfanaSaleEnElInforme() {
        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")), List.of(), List.of(),
                List.of(sectionInsulator("EP6", "HERZLIYA", "B7")),
                List.of(sectionInsulatorSwitch("EP6", "HERZLIYA", "B7", "W31", true),
                        // 'B8' no esta en la hoja de aisladores: una errata en la celda.
                        sectionInsulatorSwitch("EP6", "HERZLIYA", "B8", "W41", true)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.reference()).contains("B8", "W41");
                    assertThat(error.message()).contains("B8", "SECTION_INSULATORS");
                    assertThat(error.row()).isEqualTo(3);
                });
        // La que si tiene aislador se importa igual: un error no arrastra a la de al lado.
        assertThat(report.getSwitchesWritten()).isEqualTo(1);
    }

    /**
     * El aislador deshabilitado ya tiene su propia linea en el informe (omitido), asi que repetirla
     * por cada una de sus agujas seria ruido sobre un problema que ya esta contado.
     */
    @Test
    @DisplayName("las agujas de un aislador deshabilitado no se cuentan como huerfanas")
    void lasAgujasDeUnAisladorDeshabilitadoNoSonHuerfanas() {
        SectionInsulatorMasterRow deshabilitado = new SectionInsulatorMasterRow("EP6", "HERZLIYA", "B7",
                new BigDecimal("110176"), "TRACK_CONNECTION", "TRACK 1", null, false, 2);

        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")), List.of(), List.of(),
                List.of(deshabilitado),
                List.of(sectionInsulatorSwitch("EP6", "HERZLIYA", "B7", "W31", true)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getSkippedDisabled()).isEqualTo(1);
    }

    /**
     * Una aguja fuera de servicio se importa DESHABILITADA, no se descarta.
     *
     * <p>Es la diferencia con la mensula, y no es un descuido: la aguja es un hijo que se
     * reconcilia con {@code mergeCollection}, asi que dejarla fuera de la lista no seria "no la
     * cargues" sino BORRARLA, con su id y su historico. Una aguja fuera de servicio sigue estando
     * en el plano y el equipo necesita verla marcada; lo que la borra es quitar su fila de la hoja.
     */
    @Test
    @DisplayName("una aguja deshabilitada llega al upsert, no se queda por el camino")
    void laAgujaDeshabilitadaSeImportaMarcada() {
        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")), List.of(), List.of(),
                List.of(sectionInsulator("EP6", "HERZLIYA", "B7")),
                List.of(sectionInsulatorSwitch("EP6", "HERZLIYA", "B7", "W31", true),
                        sectionInsulatorSwitch("EP6", "HERZLIYA", "B7", "W41", false)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SectionInsulatorSwitchMasterRow>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(upsertService).upsertSectionInsulator(any(), anyLong(), any(), any(),
                captor.capture(), any(), anyBoolean());

        assertThat(captor.getValue())
                .extracting(SectionInsulatorSwitchMasterRow::code, SectionInsulatorSwitchMasterRow::enabled)
                .containsExactlyInAnyOrder(tuple("W31", true), tuple("W41", false));
        // Las dos cuentan como escritas: la deshabilitada tambien es una fila.
        assertThat(report.getSwitchesWritten()).isEqualTo(2);
        assertThat(report.getFailed()).isZero();
    }

    /**
     * El informe distingue los tres estados, no solo alta y modificacion.
     *
     * <p>No es contabilidad decorativa: 'sin cambios' es lo que dice que una reimportacion no ha
     * reescrito nada, y reescribir un paquete, una estacion o una via materializa su subarbol
     * entero. Si el contador se quedara mudo, la unica senal de que la deteccion ha dejado de
     * funcionar seria que la carga tarda horas.
     */
    @Test
    @DisplayName("lo que no ha cambiado se cuenta aparte, ni como alta ni como modificacion")
    void loQueNoHaCambiadoSeCuentaAparte() {
        when(upsertService.upsertExecutionPackage(any(), anyBoolean()))
                .thenReturn(UpsertResult.unchanged(1L));
        when(upsertService.upsertStation(any(), anyLong(), anyBoolean()))
                .thenReturn(UpsertResult.unchanged(2L));
        when(upsertService.upsertTrack(any(), anyLong(), any(), anyBoolean()))
                .thenReturn(UpsertResult.unchanged(3L));

        givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                List.of(track("EP6", "TRACK 1", "HERZLIYA")),
                List.of(profile("EP6", "TRACK 1", "83-1.02")),
                List.of(cantilever("EP6", "TRACK 1", "83-1.02", 1)));

        ProfileImportReport report = importer.importFrom(ANY_FILE, false);

        for (String entidad : List.of(ProfileImportReport.EXECUTION_PACKAGE,
                ProfileImportReport.STATION, ProfileImportReport.TRACK)) {
            assertThat(report.outcomeOf(entidad).getUnchanged()).as(entidad).isEqualTo(1);
            assertThat(report.outcomeOf(entidad).getCreated()).as(entidad).isZero();
            assertThat(report.outcomeOf(entidad).getUpdated()).as(entidad).isZero();
        }
        assertThat(report.getUnchanged()).isEqualTo(3);
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
                .thenAnswer(invocation -> UpsertResult.created(siguiente.getAndIncrement()));

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
                .thenReturn(UpsertResult.created(9L));

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
                List.of(profile("EP6", "TRACK 1", "A", 1), profile("EP6", "TRACK 1", "B", 2)),
                List.of(cantilever("EP6", "TRACK 1", "A", 1, 1),
                        cantilever("EP6", "TRACK 1", "A", 1, 2),
                        cantilever("EP6", "TRACK 1", "B", 2, 1)));

        importer.importFrom(ANY_FILE, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CantileverMasterRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(upsertService, org.mockito.Mockito.times(2))
                .upsertProfile(any(), anyLong(), captor.capture(), anyBoolean());

        assertThat(captor.getAllValues().get(0)).hasSize(2);
        assertThat(captor.getAllValues().get(1)).hasSize(1);
    }

    @Test
    @DisplayName("dos perfiles con el MISMO identificador no se reparten mal las mensulas")
    void mensulasConIdentificadorRepetido() {
        // Es el caso de una via con dos tramos concatenados: 'A' existe dos veces, con
        // distinto KP. Agrupando por identificador, cada uno se llevaba las mensulas de los
        // dos —hasta seis, el doble del maximo que admite un perfil—. Las separa el ORDEN.
        givenMaster(List.of(ep("EP9A")), List.of(), List.of(track("EP9A", "TRACK 1", "")),
                List.of(profile("EP9A", "TRACK 1", "A", 1), profile("EP9A", "TRACK 1", "A", 2)),
                List.of(cantilever("EP9A", "TRACK 1", "A", 1, 1),
                        cantilever("EP9A", "TRACK 1", "A", 2, 1),
                        cantilever("EP9A", "TRACK 1", "A", 2, 2)));

        importer.importFrom(ANY_FILE, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CantileverMasterRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(upsertService, org.mockito.Mockito.times(2))
                .upsertProfile(any(), anyLong(), captor.capture(), anyBoolean());

        assertThat(captor.getAllValues().get(0)).hasSize(1);
        assertThat(captor.getAllValues().get(1)).hasSize(2);
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
        givenMaster(eps, stations, tracks, profiles, cantilevers, List.of(), List.of());
    }

    private void givenMaster(List<ExecutionPackageMasterRow> eps, List<StationMasterRow> stations,
                             List<TrackMasterRow> tracks, List<ProfileMasterRow> profiles,
                             List<CantileverMasterRow> cantilevers,
                             List<SectionInsulatorMasterRow> sectionInsulators,
                             List<SectionInsulatorSwitchMasterRow> switches) {
        when(parser.parseAll(any())).thenReturn(new ProfileMasterParser.ProfileMasterContent(
                new ArrayList<>(eps), new ArrayList<>(stations), new ArrayList<>(tracks),
                new ArrayList<>(profiles), new ArrayList<>(cantilevers),
                new ArrayList<>(sectionInsulators), new ArrayList<>(switches)));
    }

    private static SectionInsulatorMasterRow sectionInsulator(String ep, String station, String name) {
        return new SectionInsulatorMasterRow(ep, station, name, new BigDecimal("110176"),
                "TRACK_CONNECTION", "TRACK 1", null, true, 2);
    }

    private static SectionInsulatorSwitchMasterRow sectionInsulatorSwitch(String ep, String station,
                                                                          String insulator, String code,
                                                                          boolean enabled) {
        return new SectionInsulatorSwitchMasterRow(ep, station, insulator, code,
                new BigDecimal("110176"), 9, "TRACK 1", enabled, 3);
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
        return profile(ep, track, profileId, 1);
    }

    /**
     * El ORDEN identifica al perfil dentro de la via, y es lo que agrupa sus mensulas. Dos
     * perfiles de la misma via tienen que llevar ORDEN distinto: el identificador no basta,
     * porque una via con dos tramos concatenados lo repite a proposito.
     */
    private static ProfileMasterRow profile(String ep, String track, String profileId, int orden) {
        return new ProfileMasterRow(ep, track, profileId, "83063.410", orden, "DEFINITIVE",
                ProfileLovCodes.empty(), new BigDecimal("52.000"), null, null, null, true, 7);
    }

    private static CantileverMasterRow cantilever(String ep, String track, String profileId, int slot) {
        return cantilever(ep, track, profileId, 1, slot);
    }

    private static CantileverMasterRow cantilever(String ep, String track, String profileId,
                                                  int orden, int slot) {
        return new CantileverMasterRow(ep, track, profileId, orden, slot, "EMT-1", null, null, null,
                null, null, null, "PH", 1150L, true, 9);
    }
}
