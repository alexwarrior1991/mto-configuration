package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.*;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import com.alejandro.mtoconfiguration.validator.infrastructure.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoError;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.assertNoErrors;
import static com.alejandro.mtoconfiguration.validator.AlertAssert.paths;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las ocho entidades exponen endpoints de alta y modificación en lote, síncronos y asíncronos.
 *
 * <p>Antes solo {@code ProfileValidator} implementaba la validación de lote; el resto heredaba un
 * método por defecto que devolvía la lista vacía, de modo que sus endpoints aceptaban cualquier cosa
 * sin comprobar nada. Estos tests fijan que el comportamiento por defecto valide.</p>
 */
class BulkValidationTest {

    private static SteadyArmValidator steadyArm() {
        return new SteadyArmValidator();
    }

    private static CantileverValidator cantilever() {
        return new CantileverValidator(steadyArm());
    }

    private static ProfileValidator profile() {
        return new ProfileValidator(cantilever(), new DisconnectorValidator());
    }

    private static TrackValidator track() {
        return new TrackValidator(profile());
    }

    private static StationValidator station() {
        return new StationValidator(track(), new DisconnectorValidator(), new SectionInsulatorValidator());
    }

    private static ExecutionPackageValidator executionPackage() {
        return new ExecutionPackageValidator(track(), station());
    }

    /** Cada entidad con su validador, un DTO válido de raíz y el campo que se estropea. */
    private static Stream<Arguments> entidades() {
        return Stream.of(
                Arguments.of("track", track(), (Supplier<TrackDTO>) ValidDtos::rootTrack, "name"),
                Arguments.of("station", station(), (Supplier<StationDTO>) ValidDtos::rootStation, "name"),
                Arguments.of("cantilever", cantilever(), (Supplier<CantileverDTO>) ValidDtos::rootCantilever, "armAngle"),
                Arguments.of("steadyArm", steadyArm(), (Supplier<SteadyArmDTO>) ValidDtos::existingSteadyArm, "length"),
                Arguments.of("executionPackage", executionPackage(), (Supplier<ExecutionPackageDTO>) ValidDtos::rootExecutionPackage, "name"),
                Arguments.of("disconnector", new DisconnectorValidator(), (Supplier<DisconnectorDTO>) ValidDtos::rootDisconnector, "name"),
                Arguments.of("sectionInsulator", new SectionInsulatorValidator(), (Supplier<SectionInsulatorDTO>) ValidDtos::rootSectionInsulator, "name"),
                Arguments.of("profile", profile(), (Supplier<ProfileDTO>) ValidDtos::rootProfile, "profileId"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends BaseDTO> CRUDValidator<T> cast(Object validator) {
        return (CRUDValidator<T>) validator;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    @DisplayName("un lote válido pasa")
    <T extends BaseDTO> void loteValidoPasa(String nombre, Object validador, Supplier<T> valido, String campo) {
        assertNoErrors(BulkValidationTest.<T>cast(validador).validateBeforeBulkSave(lote(valido, 2)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    @DisplayName("un lote con un elemento inválido se rechaza, señalando su índice")
    <T extends BaseDTO> void loteConElementoInvalidoSeRechaza(String nombre, Object validador,
                                                              Supplier<T> valido, String campo) {
        T invalido = valido.get();
        romper(invalido, campo);

        List<T> lote = lote(valido, 1);
        lote.add(invalido);

        List<Alert> alerts = BulkValidationTest.<T>cast(validador).validateBeforeBulkSave(lote);

        assertThat(alerts)
                .withFailMessage("El lote de %s no detectó el elemento inválido", nombre)
                .isNotEmpty();
        assertError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "[1]." + campo);
        assertNoError(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD, "[0]." + campo);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    @DisplayName("un lote vacío o nulo se rechaza")
    <T extends BaseDTO> void loteVacioSeRechaza(String nombre, Object validador, Supplier<T> valido, String campo) {
        assertError(BulkValidationTest.<T>cast(validador).validateBeforeBulkSave(List.of()), ErrorCodes.VALIDATION_REQUIRED_FIELD, nombre);
        assertError(BulkValidationTest.<T>cast(validador).validateBeforeBulkSave(null), ErrorCodes.VALIDATION_REQUIRED_FIELD, nombre);
        assertError(BulkValidationTest.<T>cast(validador).validateBeforeBulkUpdate(List.of()), ErrorCodes.VALIDATION_REQUIRED_FIELD, nombre);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    @DisplayName("un hueco nulo dentro del lote se señala en su posición")
    <T extends BaseDTO> void elementoNuloSeSenala(String nombre, Object validador, Supplier<T> valido, String campo) {
        List<T> lote = Arrays.asList(valido.get(), null);

        assertError(BulkValidationTest.<T>cast(validador).validateBeforeBulkSave(lote),
                ErrorCodes.VALIDATION_REQUIRED_FIELD, "[1]");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    @DisplayName("la modificación en lote exige el id de cada elemento")
    <T extends BaseDTO> void modificacionEnLoteExigeId(String nombre, Object validador,
                                                       Supplier<T> valido, String campo) {
        T sinId = valido.get();
        sinId.setId(null);

        assertError(BulkValidationTest.<T>cast(validador).validateBeforeBulkUpdate(ValidDtos.listOf(sinId)),
                ErrorCodes.VALIDATION_REQUIRED_FIELD, "[0].id");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entidades")
    @DisplayName("los índices no se descuadran: cada error apunta a su elemento")
    <T extends BaseDTO> void losIndicesApuntanAlElementoCorrecto(String nombre, Object validador,
                                                                 Supplier<T> valido, String campo) {
        T invalido = valido.get();
        romper(invalido, campo);

        List<T> lote = lote(valido, 2);
        lote.add(invalido);

        List<Alert> alerts = BulkValidationTest.<T>cast(validador).validateBeforeBulkSave(lote);

        assertThat(paths(alerts, ErrorCodes.VALIDATION_REQUIRED_FIELD))
                .allMatch(path -> path.startsWith("[2]"));
    }

    @Test
    @DisplayName("el alta en lote de perfiles sigue detectando profileId repetidos")
    void elLoteDePerfilesSigueDetectandoDuplicados() {
        ProfileDTO uno = ValidDtos.rootProfile();
        uno.setProfileId("P-1");
        ProfileDTO dos = ValidDtos.rootProfile();
        dos.setProfileId("p-1");

        List<Alert> alerts = profile().validateBeforeBulkSave(ValidDtos.listOf(uno, dos));

        assertError(alerts, ErrorCodes.DUPLICATED_RESOURCE, "[0].profileId");
        assertError(alerts, ErrorCodes.DUPLICATED_RESOURCE, "[1].profileId");
    }

    @Test
    @DisplayName("un lote vacío de perfiles no intenta buscar duplicados")
    void loteVacioDePerfilesNoBuscaDuplicados() {
        assertThat(paths(profile().validateBeforeBulkSave(List.of()), ErrorCodes.DUPLICATED_RESOURCE)).isEmpty();
    }

    /**
     * Lote de elementos válidos y <b>distintos entre sí</b>: un lote de clones dispararía las reglas
     * de unicidad, que es precisamente lo que comprueba otro test.
     */
    private static <T extends BaseDTO> List<T> lote(Supplier<T> valido, int tamano) {
        List<T> lote = new java.util.ArrayList<>();

        for (int i = 0; i < tamano; i++) {
            T dto = valido.get();
            distinguir(dto, i);
            lote.add(dto);
        }

        return lote;
    }

    /** Hace único el elemento en las entidades que tienen reglas de unicidad dentro del lote. */
    private static void distinguir(BaseDTO dto, int index) {
        if (dto instanceof ProfileDTO profile) {
            profile.setProfileId("P-" + index);
        }
    }

    /** Deja el campo indicado a nulo, que es como se dispara VALIDATION_REQUIRED_FIELD. */
    private static void romper(BaseDTO dto, String campo) {
        switch (dto) {
            case TrackDTO t -> t.setName(null);
            case StationDTO s -> s.setName(null);
            case CantileverDTO c -> c.setArmAngle(null);
            case SteadyArmDTO s -> s.setLength(null);
            case ExecutionPackageDTO e -> e.setName(null);
            case DisconnectorDTO d -> d.setName(null);
            case SectionInsulatorDTO s -> s.setName(null);
            case ProfileDTO p -> p.setProfileId(null);
            default -> throw new IllegalArgumentException("DTO no contemplado: " + dto.getClass());
        }
    }
}
