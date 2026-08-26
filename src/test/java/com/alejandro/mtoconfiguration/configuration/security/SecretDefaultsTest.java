package com.alejandro.mtoconfiguration.configuration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuración base es la que heredan los entornos desplegados, así que ninguno de sus
 * secretos puede llevar valor por defecto.
 *
 * <p>Un default convierte el olvido de una variable en un arranque aparentemente correcto: la
 * aplicación levanta con la contraseña de ejemplo y nadie se entera hasta que alguien la usa. Sin
 * default, el placeholder no resuelve y el arranque falla nombrando la variable que falta, que es
 * el momento y el sitio donde se quiere descubrir.</p>
 *
 * <p>Se comprueba sobre el texto del fichero y no sobre las propiedades ya resueltas porque lo que
 * se vigila es precisamente la <em>ausencia</em> del default: una vez resuelto el valor, ya no se
 * distingue de dónde salió.</p>
 */
class SecretDefaultsTest {

    /** Variables cuyo valor es un secreto y por tanto solo puede venir del entorno. */
    private static final List<String> SECRETOS = List.of(
            "MTO_CONFIGURATION_DATASOURCE_PASSWORD",
            "SPRING_RABBITMQ_PASSWORD",
            "KEYCLOAK_SERVICE_CLIENT_SECRET");

    @Test
    @DisplayName("ningún secreto de application.yaml lleva valor por defecto")
    void ningunSecretoLlevaValorPorDefecto() {
        String configuracion = leer("application.yaml");

        for (String secreto : SECRETOS) {
            // ${VARIABLE:loQueSea} — el default es todo lo que siga a los dos puntos.
            Matcher conDefecto = Pattern
                    .compile("\\$\\{" + Pattern.quote(secreto) + ":([^}]*)}")
                    .matcher(configuracion);

            // find() avanza el matcher, así que se guarda el resultado antes de usarlo dos veces.
            boolean encontrado = conDefecto.find();
            String valorPorDefecto = encontrado ? conDefecto.group(1) : "";

            assertThat(encontrado)
                    .withFailMessage(
                            "%s tiene un valor por defecto en application.yaml ('%s'). Un entorno "
                                    + "desplegado al que le falte esa variable arrancaría con él en "
                                    + "lugar de fallar.",
                            secreto, valorPorDefecto)
                    .isFalse();

            assertThat(configuracion)
                    .withFailMessage("%s no aparece en application.yaml: ¿se ha renombrado?", secreto)
                    .contains("${" + secreto + "}");
        }
    }

    @Test
    @DisplayName("la plantilla .env.example documenta todos los secretos")
    void laPlantillaDocumentaTodosLosSecretos() {
        // Vive en la raíz del proyecto, no en el classpath: la lee Docker Compose, no la aplicación.
        String plantilla = leerDeLaRaiz(".env.example");

        assertThat(SECRETOS)
                .allSatisfy(secreto -> assertThat(plantilla)
                        .withFailMessage("%s no está en .env.example, así que nadie sabrá que hace "
                                + "falta hasta que algo falle", secreto)
                        .contains(secreto + "="));
    }

    private static String leer(String recurso) {
        try {
            return new String(new ClassPathResource(recurso).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + recurso, e);
        }
    }

    private static String leerDeLaRaiz(String fichero) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(fichero));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + fichero + " en la raíz del proyecto", e);
        }
    }
}
