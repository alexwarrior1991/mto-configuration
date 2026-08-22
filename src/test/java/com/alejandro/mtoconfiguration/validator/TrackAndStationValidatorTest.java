package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.CantileverValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.ProfileValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.StationValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class TrackAndStationValidatorTest {

    private final TrackValidator trackValidator = new TrackValidator(new ProfileValidator(
            new CantileverValidator(new SteadyArmValidator()), new DisconnectorValidator()));

    private final StationValidator stationValidator = new StationValidator(
            trackValidator, new DisconnectorValidator(), new SectionInsulatorValidator());

    @Nested
    class Vias {

        @Test
        void aceptaUnaViaValida() {
            assertNoErrors(trackValidator.validateBeforeSave(ValidDtos.rootTrack()));
        }

        @Test
        void exigeLosCamposPropios() {
            List<Alert> alerts = trackValidator.validateBeforeSave(new TrackDTO());

            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "enabled");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "executionPackageId");
        }

        @Test
        @DisplayName("stationId es opcional: la columna STATION_ID de TRACK es anulable")
        void noExigeStationId() {
            TrackDTO dto = ValidDtos.rootTrack();
            dto.setStationId(null);

            assertNoErrors(trackValidator.validateBeforeSave(dto));
            assertNoError(trackValidator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "stationId");
        }

        @Test
        void propagaLosErroresDeLosPerfiles() {
            ProfileDTO malo = ValidDtos.newProfile();
            malo.setKp("no numerico");

            TrackDTO dto = ValidDtos.rootTrack();
            dto.setProfiles(ValidDtos.listOf(malo));

            assertError(trackValidator.validateBeforeSave(dto), ErrorCodes.VALIDATION_INVALID_FORMAT, "profiles[0].kp");
        }

        @Test
        @DisplayName("un perfil anidado no necesita trackId")
        void noExigeTrackIdEnLosPerfilesAnidados() {
            TrackDTO dto = ValidDtos.rootTrack();
            dto.setProfiles(ValidDtos.listOf(ValidDtos.newProfile()));

            assertNoErrors(trackValidator.validateBeforeSave(dto));
        }
    }

    @Nested
    class Estaciones {

        @Test
        void aceptaUnaEstacionValida() {
            assertNoErrors(stationValidator.validateBeforeSave(ValidDtos.rootStation()));
        }

        @Test
        void exigeLosCamposPropios() {
            List<Alert> alerts = stationValidator.validateBeforeSave(new StationDTO());

            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "executionPackageId");
        }

        @Test
        @DisplayName("valida las tres colecciones que cascadean desde Station")
        void validaSusTresColecciones() {
            StationDTO dto = ValidDtos.rootStation();

            TrackDTO track = ValidDtos.newTrack();
            track.setName(null);
            dto.setTracks(ValidDtos.listOf(track));

            var disconnector = ValidDtos.newDisconnector();
            disconnector.setOnLoad(null);
            dto.setDisconnectors(ValidDtos.listOf(disconnector));

            var insulator = ValidDtos.newSectionInsulator();
            insulator.setEnabled(null);
            dto.setSectionInsulators(ValidDtos.listOf(insulator));

            List<Alert> alerts = stationValidator.validateBeforeSave(dto);

            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "tracks[0].name");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "disconnectors[0].onLoad");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "sectionInsulators[0].enabled");
        }

        @Test
        @DisplayName("los hijos anidados no necesitan sus claves ajenas")
        void noExigeLasClavesAjenasDeLosHijos() {
            StationDTO dto = ValidDtos.rootStation();
            dto.setTracks(ValidDtos.listOf(ValidDtos.newTrack()));
            dto.setDisconnectors(ValidDtos.listOf(ValidDtos.newDisconnector()));
            dto.setSectionInsulators(ValidDtos.listOf(ValidDtos.newSectionInsulator()));

            assertNoErrors(stationValidator.validateBeforeSave(dto));
        }
    }
}
