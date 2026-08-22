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

import java.util.Arrays;
import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

/**
 * Un padre puede llegar con hijos que ya existen y con hijos nuevos <b>en la misma lista</b>.
 *
 * <p>La regla es que cada hijo se valida según su propio id, nunca según la operación del padre:
 * un hijo sin id es un alta (no se le exige id) y uno con id es una modificación (sí se le exige).
 * Estos tests fijan esa regla en las dos operaciones y en los dos niveles de anidamiento.</p>
 */
class ChildIdentityTest {

    private final TrackValidator trackValidator = new TrackValidator(new ProfileValidator(
            new CantileverValidator(new SteadyArmValidator()), new DisconnectorValidator()));

    private final StationValidator stationValidator = new StationValidator(
            trackValidator, new DisconnectorValidator(), new SectionInsulatorValidator());

    private final ExecutionPackageValidator validator =
            new ExecutionPackageValidator(trackValidator, stationValidator);

    @Test
    @DisplayName("alta del padre con hijos nuevos y existentes mezclados")
    void altaDelPadreConHijosMezclados() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setTracks(ValidDtos.listOf(viaNueva(), viaExistente(5L), viaNueva(), viaExistente(6L)));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("modificación del padre con hijos nuevos y existentes mezclados")
    void modificacionDelPadreConHijosMezclados() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setId(1L);
        dto.setTracks(ValidDtos.listOf(viaExistente(5L), viaNueva(), viaExistente(6L)));

        assertNoErrors(validator.validateBeforeUpdate(dto));
    }

    @Test
    @DisplayName("a ningún hijo sin id se le exige el id, ni siquiera actualizando el padre")
    void nuncaSeExigeIdAlHijoNuevo() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setId(1L);
        dto.setTracks(ValidDtos.listOf(viaExistente(5L), viaNueva()));

        List<Alert> alerts = validator.validateBeforeUpdate(dto);

        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0].id");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[1].id");
    }

    @Test
    @DisplayName("un hijo que dice existir pero llega sin id sigue siendo un error del hijo, no del padre")
    void elHijoInvalidoSeSenalaEnSuPropiaPosicion() {
        TrackDTO invalida = viaNueva();
        invalida.setName(null);

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setTracks(ValidDtos.listOf(viaExistente(5L), invalida, viaNueva()));

        List<Alert> alerts = validator.validateBeforeSave(dto);

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[1].name");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0].name");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[2].name");
    }

    @Test
    @DisplayName("la regla se aplica también en los nietos: estación existente con vía nueva dentro")
    void laReglaLlegaHastaLosNietos() {
        StationDTO estacion = ValidDtos.newStation();
        estacion.setId(9L);
        estacion.setTracks(ValidDtos.listOf(viaNueva(), viaExistente(7L)));

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setId(1L);
        dto.setStations(ValidDtos.listOf(estacion));

        List<Alert> alerts = validator.validateBeforeUpdate(dto);

        assertNoErrors(alerts);
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "stations[0].tracks[0].id");
    }

    @Test
    @DisplayName("una estación nueva con vías nuevas dentro se da de alta entera")
    void altaCompletaDelArbol() {
        StationDTO estacion = ValidDtos.newStation();
        estacion.setTracks(ValidDtos.listOf(viaNueva()));
        estacion.setDisconnectors(ValidDtos.listOf(ValidDtos.newDisconnector()));
        estacion.setSectionInsulators(ValidDtos.listOf(ValidDtos.newSectionInsulator()));

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setStations(ValidDtos.listOf(estacion));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("un hueco nulo dentro de la colección se señala en su posición")
    void reportaLosElementosNulosDeLaColeccion() {
        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setTracks(new java.util.ArrayList<>(Arrays.asList(viaNueva(), null, viaNueva())));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[1]");
    }

    @Test
    @DisplayName("los índices no se descuadran cuando hay elementos nulos por medio")
    void losIndicesNoSeDescuadranConNulos() {
        TrackDTO invalida = viaNueva();
        invalida.setName(null);

        ExecutionPackageDTO dto = ValidDtos.rootExecutionPackage();
        dto.setTracks(new java.util.ArrayList<>(Arrays.asList(null, invalida)));

        List<Alert> alerts = validator.validateBeforeSave(dto);

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0]");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[1].name");
    }

    /** Vía nueva: sin id y sin clave ajena, porque las dos las pone el padre al persistir. */
    private static TrackDTO viaNueva() {
        return ValidDtos.newTrack();
    }

    /** Vía que ya existe: trae id, pero tampoco necesita repetir la clave ajena del padre. */
    private static TrackDTO viaExistente(Long id) {
        TrackDTO dto = ValidDtos.newTrack();
        dto.setId(id);
        return dto;
    }
}
