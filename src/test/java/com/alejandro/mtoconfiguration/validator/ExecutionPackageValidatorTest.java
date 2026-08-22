package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.CantileverValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.ExecutionPackageValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.ProfileValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.StationValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.paths;
import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPackageValidatorTest {

    private final TrackValidator trackValidator = new TrackValidator(new ProfileValidator(
            new CantileverValidator(new SteadyArmValidator()), new DisconnectorValidator()));

    private final ExecutionPackageValidator validator = new ExecutionPackageValidator(
            trackValidator,
            new StationValidator(trackValidator, new DisconnectorValidator(), new SectionInsulatorValidator()));

    @Test
    void aceptaUnPaqueteValido() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootExecutionPackage()));
    }

    @Test
    void exigeLosCamposPropios() {
        List<Alert> alerts = validator.validateBeforeSave(new ExecutionPackageDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "initialPackage");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "length");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "startDate");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "endDate");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "companyId");
    }

    @Test
    @DisplayName("la fecha de fin no puede ser anterior a la de inicio")
    void rechazaFechasInvertidas() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 5, 31));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "endDate");
    }

    @Test
    void aceptaFechasIguales() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setStartDate(LocalDate.of(2026, 6, 1));
        dto.setEndDate(LocalDate.of(2026, 6, 1));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    // --- Hijos ---

    @Test
    @DisplayName("los errores de las vías llegan indexados")
    void propagaLosErroresDeLasVias() {
        TrackDTO malo = ValidDtos.newTrack();
        malo.setName(null);

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setTracks(ValidDtos.listOf(ValidDtos.newTrack(), malo));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[1].name");
    }

    @Test
    @DisplayName("una vía anidada no necesita executionPackageId: lo pone el mapper desde el padre")
    void noExigeLaClaveAjenaEnLasViasAnidadas() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setTracks(ValidDtos.listOf(ValidDtos.newTrack()));
        dto.setStations(ValidDtos.listOf(ValidDtos.newStation()));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("actualizar el paquete permite añadir una vía nueva, sin id")
    void permiteAnadirUnHijoNuevoAlActualizar() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setId(1L);
        dto.setTracks(ValidDtos.listOf(ValidDtos.newTrack()));

        List<Alert> alerts = validator.validateBeforeUpdate(dto);

        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0].id");
        assertNoErrors(alerts);
    }

    @Test
    @DisplayName("una vía existente dentro de un update sí necesita su id")
    void exigeElIdDeLosHijosQueYaExisten() {
        TrackDTO existente = ValidDtos.newTrack();
        existente.setId(5L);
        existente.setName(null);

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setId(1L);
        dto.setTracks(ValidDtos.listOf(existente));

        assertError(validator.validateBeforeUpdate(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0].name");
    }

    @Test
    @DisplayName("cada error del hijo se reporta una sola vez al actualizar")
    void noDuplicaLosErroresDeLosHijosAlActualizar() {
        TrackDTO malo = ValidDtos.newTrack();
        malo.setName(null);

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setId(1L);
        dto.setTracks(ValidDtos.listOf(malo));

        List<Alert> alerts = validator.validateBeforeUpdate(dto);

        assertThat(paths(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD))
                .containsExactly("tracks[0].name");
    }

    @Test
    @DisplayName("las estaciones bajan a sus propios hijos")
    void validaElArbolCompleto() {
        StationDTO station = ValidDtos.newStation();
        station.setSectionInsulators(ValidDtos.listOf(ValidDtos.newSectionInsulator()));
        station.getSectionInsulators().get(0).setEnabled(null);

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setStations(ValidDtos.listOf(station));

        assertError(validator.validateBeforeSave(dto),
                ErrorCodes.VALIDATION_REQUIRED_FIELD, "stations[0].sectionInsulators[0].enabled");
    }
}
