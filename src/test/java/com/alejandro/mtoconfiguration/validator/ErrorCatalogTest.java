package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCode;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.commons.StandardErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.SectionInsulatorValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ErrorCatalogTest {

    @ParameterizedTest
    @EnumSource(StandardErrorCodes.class)
    void todoCodigoDelCatalogoSeResuelvePorSuCodigo(StandardErrorCodes entry) {
        assertThat(StandardErrorCodes.findByCode(entry.code())).contains(entry.errorCode());
    }

    @Test
    void elResolverDevuelveVacioParaUnCodigoDesconocido() {
        assertThat(StandardErrorCodes.resolver().findByCode("NO-EXISTE")).isEmpty();
    }

    /**
     * El desajuste que había: las plantillas esperaban los argumentos en otro orden que el que
     * rellenan los validadores, así que formatear una alerta real lanzaba
     * {@code MissingFormatArgumentException} en vez de producir el mensaje.
     */
    @Test
    @DisplayName("las plantillas casan con los argumentos que emiten los validadores")
    void lasPlantillasCasanConLasAlertasReales() {
        List<Alert> alerts = new SectionInsulatorValidator()
                .validateBeforeSave(alertaDeRango());

        assertThat(alerts).isNotEmpty();

        alerts.forEach(alert -> {
            ErrorCode code = StandardErrorCodes.findByCode(alert.getMessage()).orElseThrow();

            assertThatCode(() -> code.format(alert.getFields().toArray()))
                    .withFailMessage("La plantilla de %s no admite los argumentos %s",
                            alert.getMessage(), alert.getFields())
                    .doesNotThrowAnyException();
        });
    }

    @Test
    void elMensajeDeRangoIncluyeCampoMinimoYMaximo() {
        String message = StandardErrorCodes.VALIDATION_OUT_OF_RANGE.errorCode().format("name", "1", "200");

        assertThat(message).contains("name").contains("1").contains("200");
    }

    @Test
    void elMensajeDeCampoObligatorioIncluyeElCampo() {
        assertThat(StandardErrorCodes.VALIDATION_REQUIRED_FIELD.errorCode().format("name"))
                .contains("name");
    }

    @Test
    void cadaConstanteDeErrorCodesTieneEntradaEnElCatalogo() {
        List<String> sinEntrada = List.of(
                        ErrorCodes.VALIDATION_REQUIRED_FIELD,
                        ErrorCodes.VALIDATION_INVALID_FORMAT,
                        ErrorCodes.VALIDATION_OUT_OF_RANGE,
                        ErrorCodes.DUPLICATED_RESOURCE,
                        ErrorCodes.BUSINESS_RULE_VIOLATION,
                        ErrorCodes.RESOURCE_NOT_FOUND,
                        ErrorCodes.CONCURRENCY_CONFLICT)
                .stream()
                .filter(code -> StandardErrorCodes.findByCode(code).isEmpty())
                .toList();

        assertThat(sinEntrada).isEmpty();
    }

    /** Aislador con el nombre demasiado largo: produce una alerta de rango con sus tres argumentos. */
    private static com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SectionInsulatorDTO alertaDeRango() {
        var dto = ValidDtos.rootSectionInsulator();
        dto.setName(ValidDtos.text(500));
        return dto;
    }
}
