package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorSwitchValidator;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class SectionInsulatorValidatorTest {

    private final SectionInsulatorValidator validator = new SectionInsulatorValidator(new SectionInsulatorSwitchValidator());

    @Test
    void aceptaUnAisladorValido() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootSectionInsulator()));
    }

    @Test
    void exigeLosCamposPropios() {
        List<Alert> alerts = validator.validateBeforeSave(new SectionInsulatorDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "enabled");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "stationId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("un nombre en blanco no es un nombre, aunque no sea nulo")
    void rechazaNombreEnBlanco(String name) {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulator();
        dto.setName(name);

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
    }

    @Test
    @DisplayName("el nombre admite hasta 200 caracteres, la longitud real de la columna")
    void aplicaLaLongitudDeLaColumna() {
        SectionInsulatorDTO enElLimite = ValidDtos.rootSectionInsulator();
        enElLimite.setName(ValidDtos.text(200));
        assertNoErrors(validator.validateBeforeSave(enElLimite));

        SectionInsulatorDTO pasado = ValidDtos.rootSectionInsulator();
        pasado.setName(ValidDtos.text(201));
        assertError(validator.validateBeforeSave(pasado), ErrorCodes.VALIDATION_OUT_OF_RANGE, "name");
    }

    @Test
    void noExigeStationIdComoHijo() {
        assertNoErrors(validator.validateBeforeSaveAsChild(ValidDtos.newSectionInsulator()));
    }

    @Test
    @DisplayName("acepta un aislador sobre una conexion entre dos vias, con sus agujas")
    void aceptaUnAisladorConAgujas() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootSectionInsulatorWithSwitches()));
    }

    @Test
    @DisplayName("una conexion entre vias necesita las dos vias")
    void exigeLasDosViasEnUnaConexion() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.setTrackId(null);
        dto.setConnectedTrackId(null);

        List<Alert> alerts = validator.validateBeforeSave(dto);

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "trackId");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "connectedTrackId");
    }

    @Test
    @DisplayName("una conexion no puede ser de una via consigo misma")
    void rechazaLaMismaViaDosVeces() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.setConnectedTrackId(dto.getTrackId());

        assertError(validator.validateBeforeSave(dto),
                ErrorCodes.BUSINESS_RULE_VIOLATION, "connectedTrackId");
    }

    @Test
    @DisplayName("en medio de una via no hay con que conectar")
    void rechazaViaConectadaEnUnAisladorEnVia() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.setInstallationType(SectionInsulatorInstallationType.IN_TRACK);

        assertError(validator.validateBeforeSave(dto),
                ErrorCodes.BUSINESS_RULE_VIOLATION, "connectedTrackId");
    }

    @Test
    @DisplayName("sin tipo de instalacion no se aplica ninguna de las dos reglas de vias")
    void sinTipoDeInstalacionNoExigeVias() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.setInstallationType(null);
        dto.setTrackId(null);
        dto.setConnectedTrackId(null);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    @DisplayName("dos agujas con el mismo codigo chocarian contra el indice unico")
    void rechazaDosAgujasConElMismoCodigo() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.getSwitches().get(1).setCode("w31");   // el mismo que la primera, en minuscula

        assertError(validator.validateBeforeSave(dto),
                ErrorCodes.DUPLICATED_RESOURCE, "switches[1].code");
    }

    @Test
    @DisplayName("las agujas se validan una a una, con su ruta dentro del aislador")
    void validaCadaAguja() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.getSwitches().get(0).setCode("AGUJA-31");

        assertError(validator.validateBeforeSave(dto),
                ErrorCodes.VALIDATION_INVALID_FORMAT, "switches[0].code");
    }

    @Test
    @DisplayName("una tangente fuera de rango no cabe en la columna")
    void rechazaTangenteFueraDeRango() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.getSwitches().get(0).setTurnoutDenominator(0);

        assertError(validator.validateBeforeSave(dto),
                ErrorCodes.VALIDATION_OUT_OF_RANGE, "switches[0].turnoutDenominator");
    }

    @Test
    @DisplayName("el KP y la tangente de una aguja son opcionales: el plano no los rotula todos")
    void aceptaAgujaSinKpNiTangente() {
        SectionInsulatorDTO dto = ValidDtos.rootSectionInsulatorWithSwitches();
        dto.getSwitches().forEach(each -> {
            each.setKp(null);
            each.setTurnoutDenominator(null);
        });

        assertNoErrors(validator.validateBeforeSave(dto));
    }
}
