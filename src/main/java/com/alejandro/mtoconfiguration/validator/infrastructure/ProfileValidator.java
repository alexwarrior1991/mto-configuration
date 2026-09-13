package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.HEIGHT_CANTILEVER_SUPPORT_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.POLE_GAUGE_LOCATION_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.POLE_GAUGE_LOCATION_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.PROFILE_ID_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.PROFILE_ID_MIN_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.PROFILE_MAX_CANTILEVERS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.RAIL_POLE_DISTANCE_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.RAIL_POLE_DISTANCE_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SPAN_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.SPAN_INTEGER_DIGITS;

@Component
@RequiredArgsConstructor
public class ProfileValidator extends NormalEntityValidator<ProfileDTO> {

    private static final String ENTITY_NAME = "profile";
    private static final String FIELD_PROFILE_ID = "profileId";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_TRACK_ID = "trackId";
    private static final String FIELD_PROFILE_STATUS = "profileStatus";
    private static final String FIELD_PROFILES = "profiles";
    private static final String FIELD_CANTILEVERS = "cantilevers";
    private static final String FIELD_DISCONNECTOR = "disconnector";
    private static final String FIELD_SPAN = "span";
    private static final String FIELD_HEIGHT_CANTILEVER_SUPPORT = "heightCantileverSupport";
    private static final String FIELD_POLE_GAUGE_LOCATION = "poleGaugeLocation";
    private static final String FIELD_RAIL_POLE_DISTANCE = "railPoleDistance";

    /**
     * El DTO transporta el punto kilométrico como texto pero la columna es {@code NUMERIC(12,3)} y
     * {@code @PositiveOrZero}: sin este patrón un valor no numérico pasaba la validación y reventaba
     * al mapear.
     */
    private static final String KP_PATTERN = "\\d+(\\.\\d+)?";

    private final CantileverValidator cantileverValidator;
    private final DisconnectorValidator disconnectorValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(ProfileDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getProfileId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_ID)
                .validateRequiredString(dto.getKp(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_KP)
                .validateRequiredLovDTO(dto.getProfileStatus(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_PROFILE_STATUS)
                .validateLengthField(dto.getProfileId(), PROFILE_ID_MIN_LENGTH, PROFILE_ID_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_PROFILE_ID)
                .validateFormat(dto.getKp(), KP_PATTERN, ErrorCodes.VALIDATION_INVALID_FORMAT, FIELD_KP)
                .validateBigDecimalWithPrecision(parseKp(dto.getKp()), KP_INTEGER_DIGITS, KP_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP)
                .validateMaxSize(dto.getCantilevers(), PROFILE_MAX_CANTILEVERS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_CANTILEVERS);

        validateTechnicalFields(dto, alerts);
    }

    /**
     * Los cuatro campos técnicos del perfil son <b>opcionales</b>: en los workbooks aparecen entre
     * el 30 % y el 95 % de las filas según cuál, así que exigirlos dejaría fuera la mayor parte del
     * catálogo. Lo que sí se comprueba, cuando vienen, es que quepan en su columna: aceptar aquí
     * más de lo que admite la columna solo cambia el 400 con el campo señalado por un 500 del
     * driver.
     *
     * <p>{@code railPoleDistance} es la única sin mínimo, porque el signo indica a qué lado de la
     * vía queda el poste.
     */
    private void validateTechnicalFields(ProfileDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateBigDecimalWithPrecision(dto.getSpan(), SPAN_INTEGER_DIGITS, SPAN_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_SPAN)
                .validateRange(dto.getSpan(), BigDecimal.ZERO, null,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_SPAN)
                .validateBigDecimalWithPrecision(dto.getHeightCantileverSupport(),
                        HEIGHT_CANTILEVER_SUPPORT_INTEGER_DIGITS, HEIGHT_CANTILEVER_SUPPORT_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_HEIGHT_CANTILEVER_SUPPORT)
                .validateRange(dto.getHeightCantileverSupport(), BigDecimal.ZERO, null,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_HEIGHT_CANTILEVER_SUPPORT)
                .validateBigDecimalWithPrecision(dto.getPoleGaugeLocation(),
                        POLE_GAUGE_LOCATION_INTEGER_DIGITS, POLE_GAUGE_LOCATION_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_POLE_GAUGE_LOCATION)
                .validateRange(dto.getPoleGaugeLocation(), BigDecimal.ZERO, null,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_POLE_GAUGE_LOCATION)
                .validateBigDecimalWithPrecision(dto.getRailPoleDistance(),
                        RAIL_POLE_DISTANCE_INTEGER_DIGITS, RAIL_POLE_DISTANCE_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_RAIL_POLE_DISTANCE);
    }

    @Override
    protected void validateParentReferences(ProfileDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getTrackId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_TRACK_ID);
    }

    @Override
    protected void validateNestedDtos(ProfileDTO dto, List<Alert> alerts) {
        validateChildren(alerts, dto.getCantilevers(), cantileverValidator, FIELD_CANTILEVERS);
        validateChild(alerts, dto.getDisconnector(), disconnectorValidator, FIELD_DISCONNECTOR);
    }

    /**
     * Al alta en lote se le añade lo único que no puede ver una validación elemento a elemento:
     * que el propio envío traiga identificadores repetidos.
     */
    @Override
    public List<Alert> validateBeforeBulkSave(List<ProfileDTO> dtoList) {
        List<Alert> alerts = super.validateBeforeBulkSave(dtoList);
        validateDuplicates(dtoList, alerts);

        return alerts;
    }

    @Override
    public List<Alert> validateBeforeBulkUpdate(List<ProfileDTO> dtoList) {
        List<Alert> alerts = super.validateBeforeBulkUpdate(dtoList);
        validateDuplicates(dtoList, alerts);

        return alerts;
    }

    private void validateDuplicates(List<ProfileDTO> dtoList, List<Alert> alerts) {
        if (CollectionUtils.isEmpty(dtoList)) {
            return;
        }

        validateDuplicates(dtoList, alerts, ProfileDTO::getId, FIELD_ID);
        validateDuplicates(dtoList, alerts, dto -> normalizeProfileId(dto.getProfileId()), FIELD_PROFILE_ID);
    }

    /**
     * Marca <b>todas</b> las posiciones implicadas en cada repetición, no solo la segunda, para que
     * el cliente pueda resaltar los dos campos que chocan.
     */
    private void validateDuplicates(List<ProfileDTO> dtoList,
                                    List<Alert> alerts,
                                    Function<ProfileDTO, Object> keyExtractor,
                                    String fieldName) {

        Map<Object, List<Integer>> positionsByKey = IntStream.range(0, dtoList.size())
                .filter(index -> dtoList.get(index) != null)
                .filter(index -> keyExtractor.apply(dtoList.get(index)) != null)
                .boxed()
                .collect(Collectors.groupingBy(index -> keyExtractor.apply(dtoList.get(index))));

        positionsByKey.values().stream()
                .filter(positions -> positions.size() > 1)
                .flatMap(List::stream)
                .sorted()
                .forEach(index -> alerts.add(Alert.ofDanger(
                        ErrorCodes.DUPLICATED_RESOURCE,
                        "[" + index + "]." + fieldName)));
    }

    private static BigDecimal parseKp(String kp) {
        if (StringUtils.isBlank(kp)) {
            return null;
        }

        try {
            return new BigDecimal(kp.trim());
        } catch (NumberFormatException e) {
            // El formato ya lo reporta validateFormat; aquí no hay precisión que comprobar.
            return null;
        }
    }

    private static String normalizeProfileId(String profileId) {
        return StringUtils.isBlank(profileId) ? null : profileId.trim().toUpperCase();
    }
}
