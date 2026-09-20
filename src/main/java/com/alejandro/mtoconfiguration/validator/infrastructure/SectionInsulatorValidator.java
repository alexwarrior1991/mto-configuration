package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.enums.infrastructure.SectionInsulatorInstallationType;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorSwitchDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;

@Component
@RequiredArgsConstructor
public class SectionInsulatorValidator extends NormalEntityValidator<SectionInsulatorDTO> {

    private static final String ENTITY_NAME = "sectionInsulator";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ENABLED = "enabled";
    private static final String FIELD_STATION_ID = "stationId";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_TRACK_ID = "trackId";
    private static final String FIELD_CONNECTED_TRACK_ID = "connectedTrackId";
    private static final String FIELD_SWITCHES = "switches";

    private final SectionInsulatorSwitchValidator sectionInsulatorSwitchValidator;

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(SectionInsulatorDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getEnabled(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ENABLED)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME)
                .validateBigDecimalWithPrecision(dto.getKp(), KP_INTEGER_DIGITS, KP_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP)
                .validateRange(dto.getKp(), BigDecimal.ZERO, null,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP);

        validateInstallation(dto, alerts);
        validateSwitchCodesAreUnique(dto, alerts);
    }

    /**
     * Las dos reglas que el tipo de instalación impone sobre las vías.
     *
     * <p>{@code installationType} es <b>opcional</b>, y por eso las dos reglas sólo se miran cuando
     * viene: los aisladores que ya están en base no lo traen —la columna nace vacía— y exigirlo
     * convertiría el despliegue de esta versión en una migración de datos que nadie puede rellenar
     * todavía. Cuando el cliente sí lo declara, se le exige que sea coherente.
     */
    private void validateInstallation(SectionInsulatorDTO dto, List<Alert> alerts) {
        if (dto.getInstallationType() == null) {
            return;
        }

        if (dto.getInstallationType() == SectionInsulatorInstallationType.IN_TRACK) {
            // En medio de una vía no hay con qué conectar: una vía conectada aquí es un dato que se
            // contradice a sí mismo, y aceptarlo dejaría en base un aislador que dice dos cosas.
            if (dto.getConnectedTrackId() != null) {
                alerts.add(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, FIELD_CONNECTED_TRACK_ID));
            }

            return;
        }

        // TRACK_CONNECTION: por definición son DOS vías, y dos distintas.
        check(alerts)
                .validateRequiredField(dto.getTrackId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_TRACK_ID)
                .validateRequiredField(dto.getConnectedTrackId(), ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        FIELD_CONNECTED_TRACK_ID);

        if (dto.getTrackId() != null && Objects.equals(dto.getTrackId(), dto.getConnectedTrackId())) {
            alerts.add(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, FIELD_CONNECTED_TRACK_ID));
        }
    }

    /**
     * Dos agujas con el mismo código dentro del mismo aislador.
     *
     * <p>Es lo único que la validación de cada hija por separado no puede ver, y además lo que
     * rechazaría el índice único de la tabla: sin esta comprobación el choque sale como un 500 del
     * driver en lugar de como un 400 con el campo señalado.
     *
     * <p>Lo que <b>no</b> se comprueba, a propósito: que la vía de una aguja sea una de las dos del
     * aislador. El plano trae puntos donde coinciden agujas de más de dos vías ({@code W47,W61} en
     * el mismo KP) y los datos reales todavía no existen; una regla de más rechazaría filas
     * legítimas el día de la carga.
     */
    private void validateSwitchCodesAreUnique(SectionInsulatorDTO dto, List<Alert> alerts) {
        if (CollectionUtils.isEmpty(dto.getSwitches())) {
            return;
        }

        Set<String> seen = new HashSet<>();

        for (int index = 0; index < dto.getSwitches().size(); index++) {
            SectionInsulatorSwitchDTO each = dto.getSwitches().get(index);

            if (each == null || each.getCode() == null || each.getCode().isBlank()) {
                continue;
            }

            if (!seen.add(each.getCode().trim().toUpperCase())) {
                alerts.add(Alert.ofDanger(ErrorCodes.DUPLICATED_RESOURCE,
                        FIELD_SWITCHES + "[" + index + "].code"));
            }
        }
    }

    @Override
    protected void validateParentReferences(SectionInsulatorDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredField(dto.getStationId(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_STATION_ID);
    }

    @Override
    protected void validateNestedDtos(SectionInsulatorDTO dto, List<Alert> alerts) {
        validateChildren(alerts, dto.getSwitches(), sectionInsulatorSwitchValidator, FIELD_SWITCHES);
    }
}
