package com.alejandro.mtoconfiguration.validator;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.AlertLevel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utilidades de aserción sobre las alertas de validación.
 *
 * <p>Por convención el primer elemento de {@code Alert.fields} es la ruta del campo y el resto son
 * argumentos del mensaje, así que las aserciones se hacen siempre sobre esa ruta.</p>
 */
public final class AlertAssert {

    private AlertAssert() {
    }

    /** Rutas de campo señaladas, en orden. */
    public static List<String> paths(List<Alert> alerts) {
        return alerts.stream()
                .map(alert -> alert.getFields().isEmpty() ? null : alert.getFields().get(0))
                .toList();
    }

    /** Rutas señaladas por las alertas con un código concreto. */
    public static List<String> paths(List<Alert> alerts, String errorCode) {
        return alerts.stream()
                .filter(alert -> errorCode.equals(alert.getMessage()))
                .map(alert -> alert.getFields().isEmpty() ? null : alert.getFields().get(0))
                .toList();
    }

    public static void assertNoErrors(List<Alert> alerts) {
        assertThat(alerts)
                .withFailMessage("Se esperaba una validación sin errores pero llegaron: %s", describe(alerts))
                .isEmpty();
    }

    public static void assertError(List<Alert> alerts, String errorCode, String path) {
        assertThat(paths(alerts, errorCode))
                .withFailMessage("Se esperaba %s sobre '%s'. Alertas recibidas: %s", errorCode, path, describe(alerts))
                .contains(path);
    }

    public static void assertNoError(List<Alert> alerts, String errorCode, String path) {
        assertThat(paths(alerts, errorCode))
                .withFailMessage("No se esperaba %s sobre '%s'. Alertas recibidas: %s", errorCode, path, describe(alerts))
                .doesNotContain(path);
    }

    /** Todas las alertas de validación deben ser de nivel DANGER: si no, el servicio no corta. */
    public static void assertAllDanger(List<Alert> alerts) {
        assertThat(alerts).allMatch(alert -> alert.getLevel() == AlertLevel.DANGER);
    }

    public static String describe(List<Alert> alerts) {
        return alerts.stream()
                .map(alert -> alert.getMessage() + fieldsOf(alert))
                .toList()
                .toString();
    }

    private static String fieldsOf(Alert alert) {
        return alert.getFields().toString();
    }
}
