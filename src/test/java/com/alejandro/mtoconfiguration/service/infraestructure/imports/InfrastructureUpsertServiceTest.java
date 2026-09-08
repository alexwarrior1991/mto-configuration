package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.entity.lov.CantileverType;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.entity.lov.ProfileStatus;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileLovCodes;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.BusinessEntityRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.StationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
import com.alejandro.mtoconfiguration.entity.lov.Sectioning;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.entity.lov.Anchorage;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Escritura de una entidad de infraestructura por su clave natural.
 *
 * <p>Se concentra en la resolucion de la EMPRESA porque es el unico dato del paquete que
 * no viaja en el maestro: alli va un NIF y en la entidad va un id, y esa traduccion es la
 * que puede no tener respuesta. Todo lo demas del paquete lo validan los validadores, que
 * tienen sus propios tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InfrastructureUpsertServiceTest {

    @Mock
    private ExecutionPackageService executionPackageService;
    @Mock
    private StationService stationService;
    @Mock
    private TrackService trackService;
    @Mock
    private ProfileService profileService;
    @Mock
    private ExecutionPackageRepository executionPackageRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private MasterDataService masterDataService;

    @InjectMocks
    private InfrastructureUpsertService service;

    @Nested
    @DisplayName("Resolucion de la empresa")
    class ResolucionDeLaEmpresa {

        /**
         * El caso que se da hoy con {@code topology.yml} a medias. Antes devolvia null y el
         * paquete moria mas abajo con «companyId es obligatorio»: un campo que no existe en
         * el fichero que hay que corregir.
         */
        @Test
        @DisplayName("sin NIF, dice que falta en topology.yml y no inventa una empresa")
        void sinNifElMensajeSenalaElFicheroQueHayQueCorregir() {
            assertThatThrownBy(() -> service.upsertExecutionPackage(row(""), false))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("company_identification_number")
                    .hasMessageContaining("topology.yml");

            verifyNoInteractions(businessEntityRepository);
            verify(executionPackageService, never()).create(any());
        }

        @Test
        @DisplayName("un NIF en blanco cuenta como ausente, no como NIF a buscar")
        void elNifEnBlancoNoSeBusca() {
            assertThatThrownBy(() -> service.upsertExecutionPackage(row("   "), false))
                    .isInstanceOf(ValidationException.class);

            verifyNoInteractions(businessEntityRepository);
        }

        /**
         * Lo importante es que el mensaje TRAIGA EL NIF. Con once paquetes apuntando a la
         * misma empresa, sin el valor no hay forma de saber cual de los once esta mal escrito.
         */
        @Test
        @DisplayName("un NIF que no existe se nombra en el error, y no se carga el paquete")
        void elNifDesconocidoSaleEnElMensaje() {
            when(businessEntityRepository.findByIdentificationNumber("B12345678"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.upsertExecutionPackage(row("B12345678"), false))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("B12345678")
                    .hasMessageContaining("business_entity");

            verify(executionPackageService, never()).create(any());
        }

        @Test
        @DisplayName("un NIF conocido se traduce a su identificador")
        void elNifConocidoSeTraduce() {
            when(businessEntityRepository.findByIdentificationNumber("B12345678"))
                    .thenReturn(Optional.of(company(77L)));
            when(executionPackageRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
            when(executionPackageService.create(any())).thenAnswer(i -> {
                ExecutionPackageDTO dto = i.getArgument(0);
                dto.setId(5L);
                return dto;
            });

            service.upsertExecutionPackage(row("B12345678"), false);

            ArgumentCaptor<ExecutionPackageDTO> captor =
                    ArgumentCaptor.forClass(ExecutionPackageDTO.class);
            verify(executionPackageService).create(captor.capture());
            assertThat(captor.getValue().getCompanyId()).isEqualTo(77L);
        }

        /**
         * Excel y los editores de YAML dejan espacios con facilidad, y la columna
         * {@code identification_number} es unique: un espacio de mas la haria fallar sin
         * que se vea nada raro en el fichero.
         */
        @Test
        @DisplayName("el NIF se busca sin los espacios de alrededor")
        void elNifSeBuscaConTrim() {
            when(businessEntityRepository.findByIdentificationNumber("B12345678"))
                    .thenReturn(Optional.of(company(77L)));
            when(executionPackageRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
            when(executionPackageService.create(any())).thenAnswer(i -> i.getArgument(0));

            service.upsertExecutionPackage(row("  B12345678  "), false);

            verify(businessEntityRepository).findByIdentificationNumber("B12345678");
        }

        /**
         * En simulacion no se escribe, pero SI se resuelve: un dryRun que se saltara la
         * busqueda daria el visto bueno a un maestro que la carga real rechazaria, y eso es
         * exactamente lo que una simulacion no puede hacer.
         */
        @Test
        @DisplayName("la simulacion tambien exige que la empresa exista")
        void laSimulacionTambienComprueba() {
            when(businessEntityRepository.findByIdentificationNumber("B99999999"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.upsertExecutionPackage(row("B99999999"), true))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("B99999999");

            verify(executionPackageService, never()).create(any());
        }
    }


    @Nested
    @DisplayName("Resolucion de las listas de valores")
    class ResolucionDeLasListasDeValores {

        /**
         * El fallo que esto corta es el mas caro de todos porque no se ve: MasterDataService
         * resuelve un codigo desconocido a null SIN QUEJARSE y ProfileValidator no consulta
         * ningun catalogo, asi que el perfil se guardaba con la clave ajena vacia y el informe
         * lo contaba como cargado. Medido sobre el maestro real eran 5.814 asignaciones.
         */
        @Test
        @DisplayName("un codigo que no existe impide cargar el perfil y sale nombrado")
        void elCodigoDesconocidoTumbaLaFilaConSuNombre() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getPoleTypeByCode("S1T")).thenReturn(null);

            assertThatThrownBy(() -> service.upsertProfile(profile("S1T"), 1L, List.of(), false))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("poleType")
                    .hasMessageContaining("S1T");

            verify(profileService, never()).create(any());
        }

        /**
         * Quien corrige el maestro quiere la lista entera de una pasada. Parar en el primero
         * obliga a una ejecucion por codigo malo, y son miles.
         */
        @Test
        @DisplayName("se acumulan TODOS los codigos que fallan, no solo el primero")
        void seAcumulanTodosLosQueFallan() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());

            ProfileMasterRow row = new ProfileMasterRow("EP6", "TRACK 1", "83-1.02", "1+000",
                    "DEFINITIVE",
                    new ProfileLovCodes("SEC-X", "", "", "", "PT-X", "", "", ""),
                    null, null, null, null, true, 5);

            assertThatThrownBy(() -> service.upsertProfile(row, 1L, List.of(), false))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("sectioning='SEC-X'")
                    .hasMessageContaining("poleType='PT-X'");
        }

        @Test
        @DisplayName("un hueco no se comprueba: la mayoria de columnas vienen vacias")
        void elHuecoNoSeComprueba() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(profileRepository.findByTrackIdAndProfileIdIgnoreCase(anyLong(), any()))
                    .thenReturn(Optional.empty());
            when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));

            service.upsertProfile(profile(""), 1L, List.of(), false);

            verify(masterDataService, never()).getPoleTypeByCode(any());
            verify(profileService).create(any());
        }

        @Test
        @DisplayName("tambien se comprueban los codigos de la mensula, con su slot")
        void tambienLosDeLaMensula() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getCantileverTypeByCode("H-ARM")).thenReturn(null);

            CantileverMasterRow cantilever = new CantileverMasterRow("EP6", "TRACK 1", "83-1.02",
                    2, "H-ARM", null, null, null, null, null, null, "", null, true, 5);

            assertThatThrownBy(() -> service.upsertProfile(profile(""), 1L, List.of(cantilever), false))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("cantilever[2].cantileverType")
                    .hasMessageContaining("H-ARM");
        }

        @Test
        @DisplayName("con todos los codigos resueltos el perfil se carga")
        void conTodoResueltoSeCarga() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getPoleTypeByCode("S1T")).thenReturn(new PoleType());
            when(masterDataService.getCantileverTypeByCode("EMT-1")).thenReturn(new CantileverType());
            when(profileRepository.findByTrackIdAndProfileIdIgnoreCase(anyLong(), any()))
                    .thenReturn(Optional.empty());
            when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));

            CantileverMasterRow cantilever = new CantileverMasterRow("EP6", "TRACK 1", "83-1.02",
                    1, "EMT-1", null, null, null, null, null, null, "", null, true, 5);

            service.upsertProfile(profile("S1T"), 1L, List.of(cantilever), false);

            verify(profileService).create(any());
        }

        /** La simulacion no escribe, pero comprueba igual: si no, aprobaria lo que luego falla. */
        @Test
        @DisplayName("la simulacion tambien exige que los codigos existan")
        void laSimulacionTambienComprueba() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getPoleTypeByCode("S1T")).thenReturn(null);

            assertThatThrownBy(() -> service.upsertProfile(profile("S1T"), 1L, List.of(), true))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("S1T");
        }

        /**
         * El maestro trae 'A/S P50(CS)' en una celda porque el perfil lleva los dos. Antes
         * cabia uno y la celda entera se quedaba fuera por no ser un codigo conocido.
         */
        @Test
        @DisplayName("una celda con varios seccionamientos se parte en varios")
        void variosSeccionamientosEnUnaCelda() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getSectioningByCode("A/S")).thenReturn(new Sectioning());
            when(masterDataService.getSectioningByCode("P50(CS)")).thenReturn(new Sectioning());
            when(profileRepository.findByTrackIdAndProfileIdIgnoreCase(anyLong(), any()))
                    .thenReturn(Optional.empty());
            when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));

            service.upsertProfile(conSeccionamiento("A/S P50(CS)"), 1L, List.of(), false);

            ArgumentCaptor<ProfileDTO> captor = ArgumentCaptor.forClass(ProfileDTO.class);
            verify(profileService).create(captor.capture());
            assertThat(captor.getValue().getSectionings())
                    .extracting(SectioningDTO::getCode)
                    .containsExactly("A/S", "P50(CS)");
        }

        @Test
        @DisplayName("uno solo sigue siendo uno solo")
        void unoSolo() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getSectioningByCode("A/S")).thenReturn(new Sectioning());
            when(profileRepository.findByTrackIdAndProfileIdIgnoreCase(anyLong(), any()))
                    .thenReturn(Optional.empty());
            when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));

            service.upsertProfile(conSeccionamiento("A/S"), 1L, List.of(), false);

            ArgumentCaptor<ProfileDTO> captor = ArgumentCaptor.forClass(ProfileDTO.class);
            verify(profileService).create(captor.capture());
            assertThat(captor.getValue().getSectionings()).hasSize(1);
        }

        @Test
        @DisplayName("la celda vacia deja el perfil sin seccionamientos")
        void celdaVacia() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(profileRepository.findByTrackIdAndProfileIdIgnoreCase(anyLong(), any()))
                    .thenReturn(Optional.empty());
            when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));

            service.upsertProfile(conSeccionamiento(""), 1L, List.of(), false);

            ArgumentCaptor<ProfileDTO> captor = ArgumentCaptor.forClass(ProfileDTO.class);
            verify(profileService).create(captor.capture());
            assertThat(captor.getValue().getSectionings()).isEmpty();
        }

        /** Partir la celda no puede saltarse la comprobacion: cada mitad tiene que existir. */
        @Test
        @DisplayName("si una de las partes no existe, el perfil no se carga y sale nombrada")
        void unaParteDesconocidaTumbaLaFila() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getSectioningByCode("A/S")).thenReturn(new Sectioning());
            when(masterDataService.getSectioningByCode("NO-EXISTE")).thenReturn(null);

            assertThatThrownBy(() -> service.upsertProfile(
                    conSeccionamiento("A/S NO-EXISTE"), 1L, List.of(), false))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("NO-EXISTE");

            verify(profileService, never()).create(any());
        }

        @Test
        @DisplayName("una celda con dos anclajes se parte en dos")
        void dosAnclajesEnUnaCelda() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getAnchorageByCode("FP+AnMC")).thenReturn(new Anchorage());
            when(masterDataService.getAnchorageByCode("CP+AnMC")).thenReturn(new Anchorage());
            when(profileRepository.findByTrackIdAndProfileIdIgnoreCase(anyLong(), any()))
                    .thenReturn(Optional.empty());
            when(profileService.create(any())).thenAnswer(i -> i.getArgument(0));

            ProfileMasterRow row = new ProfileMasterRow("EP7", "TRACK 1", "86-02.20", "1+000",
                    "DEFINITIVE",
                    new ProfileLovCodes("", "FP+AnMC CP+AnMC", "", "", "", "", "", ""),
                    null, null, null, null, true, 586);
            service.upsertProfile(row, 1L, List.of(), false);

            ArgumentCaptor<ProfileDTO> captor = ArgumentCaptor.forClass(ProfileDTO.class);
            verify(profileService).create(captor.capture());
            assertThat(captor.getValue().getAnchorages())
                    .extracting(AnchorageDTO::getCode)
                    .containsExactly("FP+AnMC", "CP+AnMC");
        }

        @Test
        @DisplayName("si una parte del anclaje no existe, el perfil no se carga")
        void anclajeDesconocidoTumbaLaFila() {
            when(masterDataService.getProfileStatusByCode("DEFINITIVE")).thenReturn(new ProfileStatus());
            when(masterDataService.getAnchorageByCode("FP+AnMC")).thenReturn(new Anchorage());
            when(masterDataService.getAnchorageByCode("NO-EXISTE")).thenReturn(null);

            ProfileMasterRow row = new ProfileMasterRow("EP7", "TRACK 1", "86-02.20", "1+000",
                    "DEFINITIVE",
                    new ProfileLovCodes("", "FP+AnMC NO-EXISTE", "", "", "", "", "", ""),
                    null, null, null, null, true, 586);

            assertThatThrownBy(() -> service.upsertProfile(row, 1L, List.of(), false))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("NO-EXISTE");

            verify(profileService, never()).create(any());
        }

        private ProfileMasterRow conSeccionamiento(String codes) {
            return new ProfileMasterRow("EP6", "TRACK 1", "83-1.02", "1+000", "DEFINITIVE",
                    new ProfileLovCodes(codes, "", "", "", "", "", "", ""),
                    null, null, null, null, true, 5);
        }

        private ProfileMasterRow profile(String poleType) {
            return new ProfileMasterRow("EP6", "TRACK 1", "83-1.02", "1+000", "DEFINITIVE",
                    new ProfileLovCodes("", "", "", "", poleType, "", "", ""),
                    null, null, null, null, true, 5);
        }
    }

    private ExecutionPackageMasterRow row(String nif) {
        return new ExecutionPackageMasterRow("EP6", "EP-06", false, 21000L,
                LocalDate.of(2018, 1, 25), LocalDate.of(2020, 12, 31), nif, true, 2);
    }

    private BusinessEntity company(Long id) {
        BusinessEntity entity = new BusinessEntity();
        entity.setId(id);
        return entity;
    }
}
