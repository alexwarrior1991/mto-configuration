package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.DisconnectorMasterRow;
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
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(upsertService.upsertDisconnector(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(UpsertResult.created(6L));
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
        givenMaster(eps, stations, tracks, profiles, cantilevers, sectionInsulators, switches, List.of());
    }

    private void givenMaster(List<ExecutionPackageMasterRow> eps, List<StationMasterRow> stations,
                             List<TrackMasterRow> tracks, List<ProfileMasterRow> profiles,
                             List<CantileverMasterRow> cantilevers,
                             List<SectionInsulatorMasterRow> sectionInsulators,
                             List<SectionInsulatorSwitchMasterRow> switches,
                             List<DisconnectorMasterRow> disconnectors) {
        when(parser.parseAll(any())).thenReturn(new ProfileMasterParser.ProfileMasterContent(
                new ArrayList<>(eps), new ArrayList<>(stations), new ArrayList<>(tracks),
                new ArrayList<>(profiles), new ArrayList<>(cantilevers),
                new ArrayList<>(sectionInsulators), new ArrayList<>(switches),
                new ArrayList<>(disconnectors)));
    }

    @Nested
    @DisplayName("Seccionadores")
    class Seccionadores {

        /**
         * Cada perfil escrito devuelve un id distinto, sacado de su fila: es lo que deja ver CUAL de
         * dos postes con el mismo identificador se ha llevado el seccionador.
         */
        @BeforeEach
        void cadaPerfilConSuId() {
            when(upsertService.upsertProfile(any(), anyLong(), any(), anyBoolean()))
                    .thenAnswer(invocation -> UpsertResult.created(
                            1000L + invocation.<ProfileMasterRow>getArgument(0).sourceRow()));
        }

        @Test
        @DisplayName("uno en un poste se escribe con el id de su poste y sin via propia")
        void enUnPoste() {
            DisconnectorMasterRow row = disconnector("HER-01", "TRACK 1", "83-1.02", null);
            givenDisconnectors(List.of(pole("83-1.02", "83063.410", 1, 7, true)), row);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getFailed()).as("%s", report.getErrors()).isZero();
            assertThat(report.outcomeOf(ProfileImportReport.DISCONNECTOR).getCreated()).isEqualTo(1);
            verify(upsertService).upsertDisconnector(eq(row), eq(2L), eq(1007L), isNull(), isNull(), eq(false));
        }

        /**
         * Una via que no existe no tumba la fila: se queda a null, como la de un aislador. El
         * seccionador sigue siendo de su estacion y tiene su KP.
         */
        @Test
        @DisplayName("uno sin poste lleva la via de la fila, o ninguna si la via no existe")
        void sinPoste() {
            DisconnectorMasterRow conVia = poleLess("HER-T1", "TRACK 1");
            DisconnectorMasterRow viaDesconocida = poleLess("HER-T9", "TRACK 9");
            givenDisconnectors(List.of(), conVia, viaDesconocida);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getFailed()).as("%s", report.getErrors()).isZero();
            verify(upsertService).upsertDisconnector(eq(conVia), eq(2L), isNull(), eq(3L), isNull(), eq(false));
            verify(upsertService)
                    .upsertDisconnector(eq(viaDesconocida), eq(2L), isNull(), isNull(), isNull(), eq(false));
        }

        /**
         * EP9A: dos tramos concatenados en la misma via repiten los identificadores de poste. El
         * identificador no basta, y lo que los distingue es su KP.
         */
        @Test
        @DisplayName("en una via que repite el poste, KP_POSTE dice cual es")
        void posteRepetidoConKpPoste() {
            DisconnectorMasterRow row = disconnector("HER-01", "TRACK 1", "5-1.03", "5000.000");
            givenDisconnectors(List.of(pole("5-1.03", "100", 1, 7, true), pole("5-1.03", "5000", 2, 8, true)),
                    row);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getFailed()).as("%s", report.getErrors()).isZero();
            verify(upsertService).upsertDisconnector(eq(row), eq(2L), eq(1008L), isNull(), isNull(), eq(false));
        }

        @Test
        @DisplayName("sin KP_POSTE, el poste repetido sale en el informe con los KP de los dos")
        void posteRepetidoSinKpPoste() {
            givenDisconnectors(List.of(pole("5-1.03", "100", 1, 7, true), pole("5-1.03", "5000", 2, 8, true)),
                    disconnector("HER-01", "TRACK 1", "5-1.03", null));

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors())
                    .extracting(ProfileImportReport.ItemError::entity, ProfileImportReport.ItemError::row)
                    .containsExactly(tuple(ProfileImportReport.DISCONNECTOR, 11));
            assertThat(report.getErrors().getFirst().message())
                    .contains("2 postes '5-1.03'", "100, 5000", "KP_POSTE");
            verify(upsertService, never()).upsertDisconnector(any(), any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("un KP_POSTE que no es el de ninguno de los dos dice donde estan")
        void kpPosteQueNoCasa() {
            givenDisconnectors(List.of(pole("5-1.03", "100", 1, 7, true), pole("5-1.03", "5000", 2, 8, true)),
                    disconnector("HER-01", "TRACK 1", "5-1.03", "2500"));

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors()).singleElement()
                    .extracting(ProfileImportReport.ItemError::message).asString()
                    .contains("KP_POSTE 2500", "100, 5000");
        }

        /**
         * Con un solo poste con ese identificador, KP_POSTE no se mira: quien cambia de poste en la
         * hoja no tiene por que acordarse de cambiar tambien su KP.
         */
        @Test
        @DisplayName("con un solo poste con ese identificador, KP_POSTE no se mira")
        void kpPosteIgnoradoSiNoHaceFalta() {
            DisconnectorMasterRow row = disconnector("HER-01", "TRACK 1", "83-1.02", "1");
            givenDisconnectors(List.of(pole("83-1.02", "83063.410", 1, 7, true)), row);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getFailed()).as("%s", report.getErrors()).isZero();
            verify(upsertService).upsertDisconnector(eq(row), eq(2L), eq(1007L), isNull(), isNull(), eq(false));
        }

        @Test
        @DisplayName("un poste que no esta en la hoja PROFILES sale en el informe con su via")
        void posteQueNoEsta() {
            givenDisconnectors(List.of(pole("83-1.02", "83063.410", 1, 7, true)),
                    disconnector("HER-01", "TRACK 1", "83-1.99", null));

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors()).singleElement()
                    .extracting(ProfileImportReport.ItemError::message).asString()
                    .contains("'83-1.99'", "'TRACK 1'", ProfileMasterParser.PROFILES_SHEET);
        }

        /**
         * El poste esta en la hoja pero no se ha escrito: deshabilitado, o fallo. No es lo mismo
         * que no existir, y el mensaje no lo confunde.
         */
        @Test
        @DisplayName("un poste deshabilitado no se ha podido cargar, y asi se dice")
        void posteDeshabilitado() {
            givenDisconnectors(List.of(pole("83-1.02", "83063.410", 1, 7, false)),
                    disconnector("HER-01", "TRACK 1", "83-1.02", null));

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors()).singleElement()
                    .extracting(ProfileImportReport.ItemError::message).asString()
                    .contains("'83-1.02'", "no se ha podido cargar");
            verify(upsertService, never()).upsertDisconnector(any(), any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("dos filas en el mismo poste: la segunda sale en el informe con el nombre de la primera")
        void dosEnElMismoPoste() {
            DisconnectorMasterRow primero = disconnector("HER-01", "TRACK 1", "83-1.02", null);
            DisconnectorMasterRow segundo = new DisconnectorMasterRow("EP6", "HERZLIYA", "TRACK 1", "", "83-1.02",
                    null, "HER-02", "", "SI", "NO", "MOTOR", "Disc/IO", true, 12);
            givenDisconnectors(List.of(pole("83-1.02", "83063.410", 1, 7, true)), primero, segundo);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors())
                    .extracting(ProfileImportReport.ItemError::row, ProfileImportReport.ItemError::message)
                    .containsExactly(tuple(12, "su poste '83-1.02' ya lo lleva 'HER-01' en esta misma hoja, "
                            + "y un poste admite un solo seccionador"));
            verify(upsertService).upsertDisconnector(eq(primero), any(), any(), any(), any(), anyBoolean());
            verify(upsertService, never()).upsertDisconnector(eq(segundo), any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("sin su estacion no se intenta, y el motivo es la estacion")
        void sinEstacion() {
            DisconnectorMasterRow row = new DisconnectorMasterRow("EP6", "HADERA", "TRACK 1", "", "",
                    null, "HAD-01", "", "SI", "NO", "MOTOR", "Disc/IO", true, 11);
            givenDisconnectors(List.of(), row);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors()).singleElement()
                    .extracting(ProfileImportReport.ItemError::message).asString()
                    .isEqualTo("su estacion 'HADERA' no se ha podido cargar");
        }

        @Test
        @DisplayName("ENABLED=NO se salta y se cuenta, como en las demas hojas")
        void deshabilitado() {
            DisconnectorMasterRow row = new DisconnectorMasterRow("EP6", "HERZLIYA", "TRACK 1", "", "",
                    null, "HER-01", "", "SI", "NO", "MOTOR", "Disc/IO", false, 11);
            givenDisconnectors(List.of(), row);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getSkippedDisabled()).isEqualTo(1);
            verify(upsertService, never()).upsertDisconnector(any(), any(), any(), any(), any(), anyBoolean());
        }

        /**
         * En simulacion no se escribe el poste, asi que no tiene id; la fila se cuenta igual que en
         * la carga real, que es lo que hace util un dryRun.
         */
        @Test
        @DisplayName("en simulacion, un poste que es un alta llega sin id y la fila se cuenta igual")
        void simulacion() {
            when(upsertService.upsertProfile(any(), any(), any(), eq(true)))
                    .thenReturn(UpsertResult.created(null));
            DisconnectorMasterRow row = disconnector("HER-01", "TRACK 1", "83-1.02", null);
            givenDisconnectors(List.of(pole("83-1.02", "83063.410", 1, 7, true)), row);

            ProfileImportReport report = importer.importFrom(ANY_FILE, true);

            assertThat(report.getFailed()).as("%s", report.getErrors()).isZero();
            assertThat(report.outcomeOf(ProfileImportReport.DISCONNECTOR).getCreated()).isEqualTo(1);
            verify(upsertService).upsertDisconnector(eq(row), any(), isNull(), isNull(), isNull(), eq(true));
        }

        @Test
        @DisplayName("el fallo del upsert llega al informe con la clave natural del seccionador")
        void falloDelUpsert() {
            when(upsertService.upsertDisconnector(any(), any(), any(), any(), any(), anyBoolean()))
                    .thenThrow(new ValidationException("DRIVE_TYPE 'DIESEL' no es MOTOR ni MANUAL"));
            givenDisconnectors(List.of(), poleLess("HER-T1", "TRACK 1"));

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getErrors())
                    .extracting(ProfileImportReport.ItemError::reference, ProfileImportReport.ItemError::message)
                    .containsExactly(tuple("EP6 / HERZLIYA / HER-T1", "DRIVE_TYPE 'DIESEL' no es MOTOR ni MANUAL"));
        }

        /**
         * La via conectada se busca en su paquete, como la del aislador, y viaja igual con poste que
         * sin el: es del propio seccionador, no de su poste.
         */
        @Test
        @DisplayName("la via conectada se resuelve en su paquete y viaja con poste o sin el")
        void viaConectada() {
            when(upsertService.upsertTrack(argThat(track -> track != null && "TRACK 2".equals(track.name())),
                    anyLong(), any(), anyBoolean())).thenReturn(UpsertResult.created(30L));
            DisconnectorMasterRow enPoste = parallel("HER-B01", "83-1.02", "TRACK 2");
            DisconnectorMasterRow sinPoste = parallel("HER-B02", "", "track 2");
            givenDisconnectorsOnTwoTracks(List.of(pole("83-1.02", "83063.410", 1, 7, true)), enPoste, sinPoste);

            ProfileImportReport report = importer.importFrom(ANY_FILE, false);

            assertThat(report.getFailed()).as("%s", report.getErrors()).isZero();
            verify(upsertService).upsertDisconnector(eq(enPoste), eq(2L), eq(1007L), isNull(), eq(30L), eq(false));
            verify(upsertService).upsertDisconnector(eq(sinPoste), eq(2L), isNull(), eq(3L), eq(30L), eq(false));
        }

        /**
         * Perder en silencio la via conectada dejaria contada una fila que no dice lo que la hoja, al
         * contrario que la via propia de uno sin poste. Y una fila que falla por ella no se queda con
         * su poste: lo puede llevar la siguiente. Igual en la simulacion, que no tiene ids.
         */
        @Test
        @DisplayName("una via conectada de otro paquete, o la propia, señala la fila y no le quita el poste a otra")
        void viaConectadaQueNoVale() {
            DisconnectorMasterRow desconocida = parallel("HER-B01", "83-1.02", "TRACK 9");
            DisconnectorMasterRow propia = parallel("HER-B02", "", "Track 1");
            DisconnectorMasterRow despues = disconnector("HER-01", "TRACK 1", "83-1.02", null);
            givenDisconnectorsOnTwoTracks(List.of(pole("83-1.02", "83063.410", 1, 7, true)),
                    desconocida, propia, despues);

            ProfileImportReport report = importer.importFrom(ANY_FILE, true);

            assertThat(report.getErrors())
                    .extracting(ProfileImportReport.ItemError::reference, ProfileImportReport.ItemError::message)
                    .containsExactly(
                            tuple("EP6 / HERZLIYA / HER-B01",
                                    "su VIA_CONECTADA 'TRACK 9' no esta entre las vias cargadas del "
                                            + "paquete EP6"),
                            tuple("EP6 / HERZLIYA / HER-B02", "su VIA_CONECTADA 'Track 1' es su propia via: "
                                    + "tiene que ser la otra de las dos que pone en paralelo"));
            verify(upsertService, never())
                    .upsertDisconnector(eq(desconocida), any(), any(), any(), any(), anyBoolean());
            verify(upsertService, never()).upsertDisconnector(eq(propia), any(), any(), any(), any(), anyBoolean());
            verify(upsertService).upsertDisconnector(eq(despues), any(), eq(1007L), any(), isNull(), eq(true));
        }

        private void givenDisconnectorsOnTwoTracks(List<ProfileMasterRow> poles,
                                                   DisconnectorMasterRow... disconnectors) {
            givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                    List.of(track("EP6", "TRACK 1", "HERZLIYA"), track("EP6", "TRACK 2", "HERZLIYA")), poles,
                    List.of(), List.of(), List.of(), List.of(disconnectors));
        }

        /** Uno de puesta en paralelo de TRACK 1 con su via conectada. Sin poste, KP en blanco. */
        private static DisconnectorMasterRow parallel(String name, String profileId, String connectedTrack) {
            return new DisconnectorMasterRow("EP6", "HERZLIYA", "TRACK 1", connectedTrack, profileId, null, name,
                    "", "NO", "NO", "MOTOR", "Disc/PP", true, 13);
        }

        private void givenDisconnectors(List<ProfileMasterRow> poles, DisconnectorMasterRow... disconnectors) {
            givenMaster(List.of(ep("EP6")), List.of(station("EP6", "HERZLIYA")),
                    List.of(track("EP6", "TRACK 1", "HERZLIYA")), poles, List.of(), List.of(), List.of(),
                    List.of(disconnectors));
        }

        /** Un poste de TRACK 1, con su fila de la hoja PROFILES, que es por lo que se le encuentra. */
        private static ProfileMasterRow pole(String profileId, String kp, int orden, int sourceRow,
                                             boolean enabled) {
            return new ProfileMasterRow("EP6", "TRACK 1", profileId, kp, orden, "DEFINITIVE",
                    ProfileLovCodes.empty(), null, null, null, null, enabled, sourceRow);
        }

        private static DisconnectorMasterRow disconnector(String name, String track, String profileId,
                                                          String profileKp) {
            return new DisconnectorMasterRow("EP6", "HERZLIYA", track, "", profileId,
                    profileKp == null ? null : new BigDecimal(profileKp), name, "", "SI", "NO", "MOTOR",
                    "Disc/IO", true, 11);
        }

        private static DisconnectorMasterRow poleLess(String name, String track) {
            return new DisconnectorMasterRow("EP6", "HERZLIYA", track, "", "", null, name, "98375.5", "SI",
                    "SI", "MANUAL", "Disc/IO", true, 11);
        }
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
