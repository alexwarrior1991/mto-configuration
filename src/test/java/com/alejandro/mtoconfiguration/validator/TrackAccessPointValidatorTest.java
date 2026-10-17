package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.TrackAccessPointValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que la API exige a un punto de acceso a la vía (V28): las cifras son las de la columna, para que
 * un valor que no cabe salga como un 400 con su campo y no como un 500 del driver.
 */
class TrackAccessPointValidatorTest {

    private final TrackAccessPointValidator validator = new TrackAccessPointValidator();

    @Test
    @DisplayName("acepta un acceso con todo lo opcional informado")
    void aceptaUnAccesoCompleto() {
        assertNoErrors(validator.validateBeforeSave(ValidDtos.rootTrackAccessPoint()));
    }

    @Test
    @DisplayName("acepta un acceso sin nada de lo opcional: estación, KP, toneladas ni coordenadas")
    void aceptaUnAccesoMinimo() {
        TrackAccessPointDTO dto = ValidDtos.rootTrackAccessPoint();
        dto.setStationId(null);
        dto.setKp(null);
        dto.setMaxAxleLoadT(null);
        dto.setMaxGrossWeightT(null);
        dto.setLatitude(null);
        dto.setLongitude(null);

        assertNoErrors(validator.validateBeforeSave(dto));
    }

    @Nested
    @DisplayName("Lo obligatorio")
    class Obligatorio {

        @Test
        @DisplayName("el nombre, la vía, la clase, el lado, si admite biviales y el estado")
        void exigeLosCamposPropios() {
            TrackAccessPointDTO dto = new TrackAccessPointDTO();
            dto.setRoadRailAccess(null);
            dto.setEnabled(null);

            List<Alert> alerts = validator.validateBeforeSave(dto);

            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "trackId");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "kind");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "side");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "roadRailAccess");
            assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "enabled");
        }

        @Test
        @DisplayName("un cuerpo que no trae si admite biviales ni el estado los deja a true")
        void losPorDefectoSonTrue() {
            TrackAccessPointDTO dto = new TrackAccessPointDTO();

            assertThat(dto.getRoadRailAccess()).isTrue();
            assertThat(dto.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("la vía se exige también como hijo: el acceso nunca viaja dentro de su vía")
        void laViaSeExigeSiempre() {
            TrackAccessPointDTO dto = ValidDtos.rootTrackAccessPoint();
            dto.setTrackId(null);

            assertError(validator.validateBeforeSaveAsChild(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "trackId");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("un nombre en blanco no es un nombre, aunque no sea nulo")
        void rechazaNombreEnBlanco(String name) {
            TrackAccessPointDTO dto = ValidDtos.rootTrackAccessPoint();
            dto.setName(name);

            assertError(validator.validateBeforeSave(dto), ErrorCodes.VALIDATION_REQUIRED_FIELD, "name");
        }

        @Test
        @DisplayName("el nombre admite hasta 200 caracteres, la longitud real de la columna")
        void aplicaLaLongitudDeLaColumna() {
            TrackAccessPointDTO enElLimite = ValidDtos.rootTrackAccessPoint();
            enElLimite.setName(ValidDtos.text(200));
            assertNoErrors(validator.validateBeforeSave(enElLimite));

            TrackAccessPointDTO pasado = ValidDtos.rootTrackAccessPoint();
            pasado.setName(ValidDtos.text(201));
            assertError(validator.validateBeforeSave(pasado), ErrorCodes.VALIDATION_OUT_OF_RANGE, "name");
        }

        @Test
        @DisplayName("modificar exige el id")
        void modificarExigeElId() {
            assertError(validator.validateBeforeUpdate(ValidDtos.rootTrackAccessPoint()),
                    ErrorCodes.VALIDATION_REQUIRED_FIELD, "id");
        }
    }

    @Nested
    @DisplayName("Las cifras")
    class Cifras {

        @Test
        @DisplayName("el KP no es negativo y cabe en numeric(12, 3), como el del aislador")
        void kp() {
            assertError(validate(dto -> dto.setKp(new BigDecimal("-0.001"))), ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp");
            assertError(validate(dto -> dto.setKp(new BigDecimal("1.2345"))), ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp");
            assertError(validate(dto -> dto.setKp(new BigDecimal("1000000000"))), ErrorCodes.VALIDATION_OUT_OF_RANGE, "kp");
            assertNoErrors(validate(dto -> dto.setKp(BigDecimal.ZERO)));
        }

        @Test
        @DisplayName("las toneladas no son negativas y caben en numeric(8, 3)")
        void toneladas() {
            assertError(validate(dto -> dto.setMaxAxleLoadT(new BigDecimal("-1"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "maxAxleLoadT");
            assertError(validate(dto -> dto.setMaxGrossWeightT(new BigDecimal("-0.5"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "maxGrossWeightT");
            assertError(validate(dto -> dto.setMaxGrossWeightT(new BigDecimal("100000"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "maxGrossWeightT");
            assertError(validate(dto -> dto.setMaxAxleLoadT(new BigDecimal("22.5001"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "maxAxleLoadT");
            assertNoErrors(validate(dto -> {
                dto.setMaxAxleLoadT(BigDecimal.ZERO);
                dto.setMaxGrossWeightT(new BigDecimal("99999.999"));
            }));
        }
    }

    @Nested
    @DisplayName("Las coordenadas")
    class Coordenadas {

        @Test
        @DisplayName("van juntas: la que falta se señala como obligatoria")
        void vanJuntas() {
            assertError(validate(dto -> dto.setLongitude(null)), ErrorCodes.VALIDATION_REQUIRED_FIELD, "longitude");
            assertError(validate(dto -> dto.setLatitude(null)), ErrorCodes.VALIDATION_REQUIRED_FIELD, "latitude");
        }

        @Test
        @DisplayName("cada una dentro de su rango")
        void rango() {
            assertError(validate(dto -> dto.setLatitude(new BigDecimal("90.000001"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "latitude");
            assertError(validate(dto -> dto.setLatitude(new BigDecimal("-90.5"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "latitude");
            assertError(validate(dto -> dto.setLongitude(new BigDecimal("180.000001"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "longitude");
            assertNoErrors(validate(dto -> {
                dto.setLatitude(new BigDecimal("-90"));
                dto.setLongitude(new BigDecimal("180"));
            }));
        }

        @Test
        @DisplayName("con seis decimales como mucho: no se redondean a escondidas")
        void seisDecimales() {
            assertError(validate(dto -> dto.setLongitude(new BigDecimal("34.5741191"))),
                    ErrorCodes.VALIDATION_OUT_OF_RANGE, "longitude");
        }
    }

    private List<Alert> validate(Consumer<TrackAccessPointDTO> change) {
        TrackAccessPointDTO dto = ValidDtos.rootTrackAccessPoint();
        change.accept(dto);
        return validator.validateBeforeSave(dto);
    }
}
