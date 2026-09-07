package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.BusinessEntityRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.StationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
