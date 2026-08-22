package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.CantileverValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.ProfileValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SteadyArmValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.paths;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileValidatorTest {

    private final ProfileValidator validator = new ProfileValidator(
            new CantileverValidator(new SteadyArmValidator()),
            new DisconnectorValidator());

    @Test
    void aceptaUnPerfilValido() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootProfile()));
    }

    @Test
    void exigeLosCamposPropios() {
        List<Alert> alerts = validator.validateBeforeSave(new ProfileDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profileId");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "kp");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profileStatus");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "trackId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no soy un numero", "12,5", "1.2.3", "-5", "12abc", "1e5"})
    @DisplayName("el kp viaja como texto pero la columna es NUMERIC y @PositiveOrZero")
    void rechazaKpNoNumerico(String kp) {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setKp(kp);

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_INVALID_FORMAT, "kp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.000", "12345.678", "999999999.999", "007.5"})
    void aceptaKpNumerico(String kp) {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setKp(kp);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "1.2345"})
    @DisplayName("el kp respeta NUMERIC(12,3): 9 enteros y 3 decimales")
    void rechazaKpConPrecisionExcesiva(String kp) {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setKp(kp);

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp");
    }

    @Test
    @DisplayName("un perfil admite como mucho 3 cantilevers, igual que la entidad")
    void rechazaMasDeTresCantilevers() {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setCantilevers(ValidDtos.listOf(
                ValidDtos.newCantilever(), ValidDtos.newCantilever(),
                ValidDtos.newCantilever(), ValidDtos.newCantilever()));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "cantilevers");
    }

    @Test
    void aceptaHastaTresCantilevers() {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setCantilevers(ValidDtos.listOf(
                ValidDtos.newCantilever(), ValidDtos.newCantilever(), ValidDtos.newCantilever()));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("los errores de los hijos llegan con su índice y su ruta completa")
    void propagaLosErroresDeLosCantileversConSuRuta() {
        CantileverDTO malo = ValidDtos.newCantilever();
        malo.setArmAngle(null);

        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setCantilevers(ValidDtos.listOf(ValidDtos.newCantilever(), malo));

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "cantilevers[1].armAngle");
    }

    @Test
    @DisplayName("un cantilever nuevo dentro del perfil no necesita profileId")
    void noExigeProfileIdEnLosCantileversAnidados() {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setCantilevers(ValidDtos.listOf(ValidDtos.newCantilever()));

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    // --- Lotes ---

    @Test
    void rechazaUnLoteVacio() {
        assertError(validator.validateBeforeBulkSave(List.of()), ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles");
        assertError(validator.validateBeforeBulkSave(null), ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles");
    }

    @Test
    void aceptaUnLoteValido() {
        assertNoErrors(validator.validateBeforeBulkSave(
                ValidDtos.listOf(perfilConId("P-1", null), perfilConId("P-2", null))));
    }

    @Test
    @DisplayName("el alta en lote también detecta profileId repetidos: antes solo lo hacía el update")
    void detectaProfileIdRepetidosEnAlta() {
        List<Alert> alerts = validator.validateBeforeBulkSave(
                ValidDtos.listOf(perfilConId("P-1", null), perfilConId("P-1", null)));

        assertError(alerts, ErrorCodes.DUPLICATED_RESOURCE, "profiles[0].profileId");
        assertError(alerts, ErrorCodes.DUPLICATED_RESOURCE, "profiles[1].profileId");
    }

    @Test
    @DisplayName("el profileId se compara normalizado: espacios y mayúsculas no crean un duplicado nuevo")
    void normalizaElProfileIdAlComparar() {
        List<Alert> alerts = validator.validateBeforeBulkSave(
                ValidDtos.listOf(perfilConId("p-1", null), perfilConId("  P-1  ", null)));

        assertThat(paths(alerts, ErrorCodes.DUPLICATED_RESOURCE)).hasSize(2);
    }

    @Test
    void detectaIdsRepetidosEnActualizacion() {
        List<Alert> alerts = validator.validateBeforeBulkUpdate(
                ValidDtos.listOf(perfilConId("P-1", 1L), perfilConId("P-2", 1L)));

        assertError(alerts, ErrorCodes.DUPLICATED_RESOURCE, "profiles[0].id");
        assertError(alerts, ErrorCodes.DUPLICATED_RESOURCE, "profiles[1].id");
    }

    @Test
    @DisplayName("los errores por elemento del lote llevan el índice del elemento")
    void indexaLosErroresDelLote() {
        ProfileDTO malo = ValidDtos.rootProfile();
        malo.setProfileId(null);

        List<Alert> alerts = validator.validateBeforeBulkSave(
                ValidDtos.listOf(ValidDtos.rootProfile(), malo));

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles[1].profileId");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles[0].profileId");
    }

    @Test
    void elLoteDeActualizacionExigeElId() {
        List<Alert> alerts = validator.validateBeforeBulkUpdate(ValidDtos.listOf(ValidDtos.rootProfile()));

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profiles[0].id");
    }

    private static ProfileDTO perfilConId(String profileId, Long id) {
        ProfileDTO dto = ValidDtos.rootProfile();
        dto.setProfileId(profileId);
        dto.setId(id);
        return dto;
    }
}
