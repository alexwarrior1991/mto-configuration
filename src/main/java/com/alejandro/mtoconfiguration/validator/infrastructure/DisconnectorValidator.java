package com.alejandro.mtoconfiguration.validator.infrastructure;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.DisconnectorDTO;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.NormalEntityValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_FRACTION_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.KP_INTEGER_DIGITS;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MAX_LENGTH;
import static com.alejandro.mtoconfiguration.core.constraints.InfrastructureConstraints.NAME_MIN_LENGTH;

@Component
public class DisconnectorValidator extends NormalEntityValidator<DisconnectorDTO> {

    private static final String ENTITY_NAME = "disconnector";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ON_LOAD = "onLoad";
    private static final String FIELD_STATION_ID = "stationId";
    private static final String FIELD_KP = "kp";
    private static final String FIELD_CONNECTED_TRACK_ID = "connectedTrackId";
    private static final String FIELD_DISCONNECTOR_FUNCTION = "disconnectorFunction";

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    @Override
    protected void validateRequiredFields(DisconnectorDTO dto, List<Alert> alerts) {
        check(alerts)
                .validateRequiredString(dto.getName(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_NAME)
                .validateRequiredField(dto.getOnLoad(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_ON_LOAD)
                .validateRequiredLovDTO(dto.getDisconnectorFunction(), ErrorCodes.VALIDATION_REQUIRED_FIELD, FIELD_DISCONNECTOR_FUNCTION)
                .validateLengthField(dto.getName(), NAME_MIN_LENGTH, NAME_MAX_LENGTH,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_NAME)
                .validateFormat(dto.getKp(), KpText.PATTERN, ErrorCodes.VALIDATION_INVALID_FORMAT, FIELD_KP)
                .validateBigDecimalWithPrecision(KpText.parse(dto.getKp()), KP_INTEGER_DIGITS, KP_FRACTION_DIGITS,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE, FIELD_KP);

        validateOwnLocation(dto, alerts);
    }

    /**
     * El KP propio es solo de un seccionador sin poste (V26): el de uno en un poste es el de su
     * perfil, y guardarlo dos veces dejaría dos datos que pueden contradecirse.
     *
     * <p>La vía propia ({@code trackId}) no: sin poste es la suya, y con poste es la que maniobra
     * cuando no es la del poste. En un haz de vías (talleres, cocheras) un seccionador cuelga a veces
     * del poste de una vía y maniobra otra, o une dos que no son la del poste; sin ella, su vía sería
     * la del poste, y no la que aísla. Con poste es opcional, y la del propio poste no se guarda dos
     * veces: la deja a null {@code DisconnectorMapper}, que es quien conoce la vía del perfil.
     *
     * <p>Va aquí y no en {@link #validateParentReferences}, porque vale también cuando el seccionador
     * viaja dentro de su estación.
     */
    private void validateOwnLocation(DisconnectorDTO dto, List<Alert> alerts) {
        if (dto.getProfileId() != null && dto.getKp() != null) {
            alerts.add(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, FIELD_KP));
        }

        validateConnectedTrack(dto, alerts);
    }

    /**
     * La vía conectada (V27) es la otra de las dos que el seccionador pone en paralelo: la suya no
     * puede serlo. Cuando trae {@code trackId}, con poste o sin él, la suya es esa, y se compara
     * aquí; con poste y sin {@code trackId} es la de su perfil, que el DTO no trae, y la compara
     * {@code DisconnectorMapper} con la entidad.
     */
    private void validateConnectedTrack(DisconnectorDTO dto, List<Alert> alerts) {
        if (dto.getConnectedTrackId() != null && Objects.equals(dto.getConnectedTrackId(), dto.getTrackId())) {
            alerts.add(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, FIELD_CONNECTED_TRACK_ID));
        }
    }

    /**
     * Ni la estación ni el poste ({@code profileId}) son obligatorios: un seccionador en plena vía, en
     * una zona neutra o en una subestación no es de ninguna estación, y los de los pórticos de
     * subestación y los de puesta a tierra no están en un poste. Pero tiene que estar en algún sitio:
     * sin estación, en un poste o con su vía propia (V26), y si no, el 400 va sobre la estación.
     *
     * <p>Solo cuando viaja solo: dentro de su estación, la estación es la del padre.
     */
    @Override
    protected void validateParentReferences(DisconnectorDTO dto, List<Alert> alerts) {
        if (dto.getStationId() == null && dto.getProfileId() == null && dto.getTrackId() == null) {
            alerts.add(Alert.ofDanger(ErrorCodes.BUSINESS_RULE_VIOLATION, FIELD_STATION_ID));
        }
    }
}
