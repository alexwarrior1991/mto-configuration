package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackAccessPointDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.COORDINATE_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.COORDINATE_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LATITUDE_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LATITUDE_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LONGITUDE_MAX;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.LONGITUDE_MIN;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TONNES_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.TONNES_INTEGER_DIGITS;

/**
 * Lo que la API exige a un punto de acceso a la vía (V28), con las mismas cifras que la columna, para
 * que un valor que no cabe salga como un 400 con su campo y no como un 500 del driver.
 *
 * <p>La vía es un campo propio y no una referencia al padre: el acceso nunca viaja anidado dentro de
 * su vía, así que se exige siempre.
 */
@Component
public class TrackAccessPointValidator extends NormalEntityValidator<TrackAccessPointDTO> {

    private static final String ENTITY_NAME = "trackAccessPoint";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_TRACK_ID = "trackId";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_SIDE = "side";
    private static final String FIELD_ROAD_RAIL_ACCESS = "roadRailAccess";
    private static final String FIELD_MAX_AXLE_LOAD = "maxAxleLoadT";
    private static final String FIELD_MAX_GROSS_WEIGHT = "maxGrossWeightT";
    private static final String FIELD_LATITUDE = "latitude";
    private static final String FIELD_LONGITUDE = "longitude";
    private static final String FIELD_ENABLED = "enabled";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(TrackAccessPointDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME)
                .validateRequiredField(dto.getTrackId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_TRACK_ID)
                .validateRequiredField(dto.getKind(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_KIND)
                .validateRequiredField(dto.getSide(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_SIDE)
                .validateRequiredField(dto.getRoadRailAccess(), ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        FIELD_ROAD_RAIL_ACCESS)
                .validateRequiredField(dto.getEnabled(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ENABLED)
                .validateBigDecimalWithPrecision(dto.getKp(), KP_INTEGER_DIGITS, KP_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP)
                .validateRange(dto.getKp(), BigDecimal.ZERO, null, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP);

        validateTonnes(dto.getMaxAxleLoadT(), FIELD_MAX_AXLE_LOAD, alerts);
        validateTonnes(dto.getMaxGrossWeightT(), FIELD_MAX_GROSS_WEIGHT, alerts);
        validateCoordinates(dto, alerts);
    }

    /** Toneladas: opcionales, no negativas y con las cifras de la columna. */
    private void validateTonnes(BigDecimal value, String field, List<Alert> alerts) {
        check(alerts)
                .validateBigDecimalWithPrecision(value, TONNES_INTEGER_DIGITS, TONNES_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, field)
                .validateRange(value, BigDecimal.ZERO, null, ErrorCodes.VALIDATION_OUT_OF_RANGE, field);
    }

    /**
     * La latitud y la longitud son opcionales, pero van juntas: una sin la otra no sitúa nada, y la
     * que falta se señala como obligatoria. Cada una dentro de su rango, con seis decimales como mucho.
     */
    private void validateCoordinates(TrackAccessPointDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredFieldIfTrueCondition(dto.getLatitude() != null, dto.getLongitude(),
                        ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_LONGITUDE)
                .validateRequiredFieldIfTrueCondition(dto.getLongitude() != null, dto.getLatitude(),
                        ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_LATITUDE)
                .validateBigDecimalWithPrecision(dto.getLatitude(), COORDINATE_INTEGER_DIGITS,
                        COORDINATE_FRACTION_DIGITS, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_LATITUDE)
                .validateRange(dto.getLatitude(), new BigDecimal(LATITUDE_MIN), new BigDecimal(LATITUDE_MAX),
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_LATITUDE)
                .validateBigDecimalWithPrecision(dto.getLongitude(), COORDINATE_INTEGER_DIGITS,
                        COORDINATE_FRACTION_DIGITS, ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_LONGITUDE)
                .validateRange(dto.getLongitude(), new BigDecimal(LONGITUDE_MIN), new BigDecimal(LONGITUDE_MAX),
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_LONGITUDE);
    }
}
