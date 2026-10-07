package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.DisconnectorValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;

class DisconnectorValidatorTest {

    private final DisconnectorValidator validator = new DisconnectorValidator();

    @Test
    void aceptaUnSeccionadorValido() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootDisconnector()));
    }

    @Test
    void exigeLosCamposPropiosYQueEsteEnAlgunSitio() {
        List<Alert> alerts = validator.validateBeforeSave(new DisconnectorDTO());

        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "onLoad");
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "disconnectorFunction");
        assertError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "stationId");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "stationId");
    }

    /**
     * Un seccionador en plena vía, en una zona neutra o en una subestación no es de ninguna estación.
     * Pero tiene que estar en algún sitio: en un poste o con su vía propia, y si no, el 400 va sobre la
     * estación, que es lo primero que se puede elegir para situarlo.
     */
    @Test
    @DisplayName("la estación es opcional, pero sin ella el seccionador va en un poste o con su vía propia")
    void estacionOpcionalSiEstaEnAlgunSitio() {
        DisconnectorDTO enPoste = ValidDtos.rootDisconnector();
        enPoste.setStationId(null);
        assertNoErrors(validator.validateBeforeSave(enPoste));

        DisconnectorDTO conViaPropia = ValidDtos.rootDisconnector();
        conViaPropia.setStationId(null);
        conViaPropia.setProfileId(null);
        conViaPropia.setKp("98375.5");
        conViaPropia.setTrackId(3L);
        assertNoErrors(validator.validateBeforeSave(conViaPropia));

        DisconnectorDTO soloEstacion = ValidDtos.rootDisconnector();
        soloEstacion.setProfileId(null);
        assertNoErrors(validator.validateBeforeSave(soloEstacion));

        DisconnectorDTO enNingunSitio = ValidDtos.rootDisconnector();
        enNingunSitio.setStationId(null);
        enNingunSitio.setProfileId(null);
        enNingunSitio.setKp("98375.5");
        List<Alert> alerts = validator.validateBeforeSave(enNingunSitio);
        assertError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "stationId");
        assertNoError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "trackId");
    }

    @Test
    @DisplayName("el poste, el estado normal y el accionamiento son opcionales: hay seccionadores que no están en un poste")
    void noExigeElPosteNiElEstadoNormalNiElAccionamiento() {
        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setProfileId(null);

        assertNoErrors(validator.validateBeforeSave(dto));

        List<Alert> alerts = validator.validateBeforeSave(new DisconnectorDTO());
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "profileId");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "normallyOpen");
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "driveType");
    }

    @Test
    @DisplayName("el KP y la vía propios son solo de un seccionador sin poste: en uno en un poste son los del perfil")
    void kpYViaSoloSinPoste() {
        DisconnectorDTO sinPoste = ValidDtos.rootDisconnector();
        sinPoste.setProfileId(null);
        sinPoste.setKp("98375.5");
        sinPoste.setTrackId(3L);
        assertNoErrors(validator.validateBeforeSave(sinPoste));

        DisconnectorDTO enPoste = ValidDtos.rootDisconnector();
        enPoste.setKp("98375.5");
        enPoste.setTrackId(3L);
        List<Alert> alerts = validator.validateBeforeSave(enPoste);
        assertError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "kp");
        assertError(alerts, ErrorCodes.BUSINESS_RULE_VIOLATION, "trackId");
    }

    /**
     * La vía conectada (V27) es la otra de las dos que pone en paralelo. Sin poste se compara aquí con
     * la propia; con poste, la del perfil no viaja en el DTO y la compara DisconnectorMapper.
     */
    @Test
    @DisplayName("la vía conectada es opcional y, sin poste, no puede ser su propia vía")
    void viaConectadaDistintaDeLaPropia() {
        DisconnectorDTO enPoste = ValidDtos.rootDisconnector();
        enPoste.setConnectedTrackId(4L);
        assertNoErrors(validator.validateBeforeSave(enPoste));

        DisconnectorDTO sinPoste = ValidDtos.rootDisconnector();
        sinPoste.setProfileId(null);
        sinPoste.setTrackId(3L);
        sinPoste.setConnectedTrackId(4L);
        assertNoErrors(validator.validateBeforeSave(sinPoste));

        DisconnectorDTO sinViaPropia = ValidDtos.rootDisconnector();
        sinViaPropia.setProfileId(null);
        sinViaPropia.setConnectedTrackId(4L);
        assertNoErrors(validator.validateBeforeSave(sinViaPropia));

        sinPoste.setConnectedTrackId(3L);
        assertError(validator.validateBeforeSave(sinPoste), ErrorCodes.BUSINESS_RULE_VIOLATION, "connectedTrackId");
    }

    @Test
    @DisplayName("el KP es un número en metros, con punto decimal y la precisión de la columna")
    void kpConFormato() {
        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setProfileId(null);

        dto.setKp("98+375");
        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_INVALID_FORMAT, "kp");
        dto.setKp("98375.1234");
        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp");
    }

    @Test
    @DisplayName("una LOV sin id ni código no sirve para resolver la referencia")
    void rechazaLovVacia() {
        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setDisconnectorFunction(new DisconnectorFunctionDTO());

        assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "disconnectorFunction");
    }

    @Test
    @DisplayName("una LOV solo con código es válida: se resuelve por código")
    void aceptaLovSoloConCodigo() {
        DisconnectorFunctionDTO lov = new DisconnectorFunctionDTO();
        lov.setCode("FN");

        DisconnectorDTO dto = ValidDtos.rootDisconnector();
        dto.setDisconnectorFunction(lov);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Test
    void noExigeLasClavesAjenasComoHijo() {
        assertNoErrors(validator.validateBeforeSaveAsChild(ValidDtos.newDisconnector()));
    }
}
