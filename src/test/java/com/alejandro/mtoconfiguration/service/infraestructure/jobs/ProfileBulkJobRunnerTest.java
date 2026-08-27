package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.enums.jobs.JobType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.jobs.JobItemErrorDTO;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * La propiedad que se prueba aqui es la que distingue esta carga masiva de {@code bulkCreate}: un
 * elemento invalido cuesta ese elemento y nada mas. Con el bulk transaccional de siempre, un fallo
 * en la posicion 9.999 deshacia los 9.998 anteriores.
 */
// Strictness relajada: los stubs de estas pruebas apuntan a UN elemento concreto (same(...)), y
// con STRICT_STUBS la llamada a los demas elementos levanta un PotentialStubbingProblem que el
// runner captura como si fuera un fallo de negocio, falseando los contadores que se estan midiendo.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileBulkJobRunnerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Mock
    private ProfileService profileService;

    @InjectMocks
    private ProfileBulkJobRunner runner;

    private final AtomicInteger sequence = new AtomicInteger();

    private ProfileJobProgress newProgress(int total) {
        AsyncJobProperties.ProfileJobs settings = new AsyncJobProperties().getProfile();
        return new ProfileJobProgress(total, settings, p -> { });
    }

    private ProfileDTO dto(Long id) {
        ProfileDTO dto = new ProfileDTO();
        dto.setId(id);
        dto.setProfileId("P-" + sequence.incrementAndGet());
        return dto;
    }

    @Test
    @DisplayName("crea cada elemento por separado y cuenta los aciertos")
    void creaElementoAElemento() {
        List<ProfileDTO> items = List.of(dto(null), dto(null), dto(null));
        ProfileJobProgress progress = newProgress(items.size());

        runner.run(JOB_ID, JobType.PROFILE_BULK_CREATE, items, progress);

        // Uno a uno, no bulkCreate: cada llamada abre su propia transaccion y con ella el progreso
        // parcial que el bulk transaccional no puede dar.
        verify(profileService, times(3)).create(any(ProfileDTO.class));
        verify(profileService, never()).update(any(ProfileDTO.class));

        assertThat(progress.getSuccessfulItems()).isEqualTo(3);
        assertThat(progress.getFailedItems()).isZero();
    }

    @Test
    @DisplayName("un elemento fallido no detiene los siguientes")
    void unFalloNoDetieneElTrabajo() {
        ProfileDTO malo = dto(null);
        List<ProfileDTO> items = List.of(dto(null), malo, dto(null));

        // same() y no el DTO a secas: BaseDTO compara por id, asi que tres altas sin id son el
        // MISMO objeto para Mockito y el doThrow se habria aplicado a las tres, dejando la prueba
        // verde por el motivo equivocado.
        doThrow(new ValidationException(List.of(Alert.ofDanger("kp obligatorio", "kp"))))
                .when(profileService).create(same(malo));

        ProfileJobProgress progress = newProgress(items.size());
        runner.run(JOB_ID, JobType.PROFILE_BULK_CREATE, items, progress);

        assertThat(progress.getProcessedItems()).isEqualTo(3);
        assertThat(progress.getSuccessfulItems()).isEqualTo(2);
        assertThat(progress.getFailedItems()).isEqualTo(1);

        JobItemErrorDTO error = progress.getItemErrors().getFirst();
        assertThat(error.index()).as("la posicion es lo unico que identifica a un alta que fallo").isEqualTo(1);
        assertThat(error.operation()).isEqualTo("create");
        assertThat(error.message()).contains("kp obligatorio").contains("kp");
    }

    @Test
    @DisplayName("en una modificacion sin id el elemento falla en vez de darse por hecho")
    void modificacionSinIdFalla() {
        ProfileDTO sinId = dto(null);
        List<ProfileDTO> items = List.of(dto(7L), sinId);

        ProfileJobProgress progress = newProgress(items.size());
        runner.run(JOB_ID, JobType.PROFILE_BULK_UPDATE, items, progress);

        // Sin la comprobacion, update() descarta el DTO sin id por su filtro Utils::exists y
        // devuelve sin hacer nada: el elemento se contaria como exitoso y el cliente creeria haber
        // modificado algo que nunca se toco.
        verify(profileService, times(1)).update(any(ProfileDTO.class));

        assertThat(progress.getSuccessfulItems()).isEqualTo(1);
        assertThat(progress.getFailedItems()).isEqualTo(1);

        JobItemErrorDTO error = progress.getItemErrors().getFirst();
        assertThat(error.index()).isEqualTo(1);
        assertThat(error.operation()).isEqualTo("update");
        assertThat(error.code()).isEqualTo("IllegalArgumentException");
    }

    @Test
    @DisplayName("un alta no exige id")
    void altaNoExigeId() {
        ProfileJobProgress progress = newProgress(1);

        runner.run(JOB_ID, JobType.PROFILE_BULK_CREATE, List.of(dto(null)), progress);

        assertThat(progress.getFailedItems()).isZero();
        assertThat(progress.getSuccessfulItems()).isEqualTo(1);
    }
}
