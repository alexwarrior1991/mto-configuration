package com.alejandro.mtoconfiguration.configuration.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los ficheros de {@code keycloak/} no los compila nadie ni los ejecuta ningún test de integración
 * salvo el realm de prueba, así que un error en ellos no se descubre hasta que alguien levanta el
 * stack o, peor, hasta que se importa en un entorno.
 *
 * <p>Esta clase vigila las dos cosas que se rompen solas con el tiempo:</p>
 *
 * <ol>
 *   <li><b>Que los dos realms no se separen.</b> {@code mto-realm-local.json} es
 *       {@code mto-realm.json} más usuarios de desarrollo, el secreto local y el grant de acceso
 *       directo. Quien añada un permiso tocando solo uno deja el stack local probando algo distinto
 *       de lo que se despliega, y eso no da error en ninguna parte.</li>
 *   <li><b>Que ningún compuesto nombre un cliente que su propio fichero no define.</b> Keycloak
 *       resuelve los roles de cliente de un compuesto contra el realm que está importando; si el
 *       cliente no está, la importación falla y el stack se queda sin realm. Es la razón por la que
 *       el {@code mto-ops} que cubre las dos aplicaciones vive en
 *       {@code mto-ops-cross-service.json} y no dentro de los realms: {@code mto-stock-api} lo
 *       crea {@code mto-stock} después, con su propia importación parcial.</li>
 * </ol>
 *
 * <p>Los <em>audience mapper</em> sí pueden nombrar un cliente ausente —su destino se resuelve al
 * emitir el token, no al importar—, y de hecho {@code mto-configuration-svc} lleva uno hacia
 * {@code mto-stock-api} desde antes de que existiera este test.</p>
 */
class RealmDefinitionsTest {

    private static final Path DIRECTORIO = Path.of("keycloak");

    private static final String API = "mto-configuration-api";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode referencia = leer("mto-realm.json");
    private final JsonNode local = leer("mto-realm-local.json");

    @Test
    @DisplayName("los dos realms declaran los mismos permisos")
    void losDosRealmsDeclaranLosMismosPermisos() {
        assertThat(permisos(local))
                .withFailMessage("""
                        Los permisos de %s no coinciden con los de mto-realm.json.
                          referencia: %s
                          local:      %s
                        El stack local estaria probando una autorizacion distinta de la que se \
                        despliega. Los dos ficheros se tocan a la vez.""",
                        "mto-realm-local.json", permisos(referencia), permisos(local))
                .isEqualTo(permisos(referencia));
    }

    @Test
    @DisplayName("los dos realms declaran los mismos perfiles con los mismos permisos dentro")
    void losDosRealmsDeclaranLosMismosPerfiles() {
        assertThat(perfiles(local))
                .withFailMessage("""
                        Los perfiles de mto-realm-local.json no coinciden con los de mto-realm.json.
                          referencia: %s
                          local:      %s""",
                        perfiles(referencia), perfiles(local))
                .isEqualTo(perfiles(referencia));
    }

    @Test
    @DisplayName("ningun compuesto nombra un cliente que su fichero no define")
    void ningunCompuestoNombraUnClienteAusente() {
        for (String fichero : List.of("mto-realm.json", "mto-realm-local.json")) {
            JsonNode realm = leer(fichero);

            Set<String> definidos = new HashSet<>();
            realm.path("clients").forEach(cliente -> definidos.add(cliente.path("clientId").asText()));

            for (JsonNode perfil : realm.path("roles").path("realm")) {
                JsonNode porCliente = perfil.path("composites").path("client");

                porCliente.fieldNames().forEachRemaining(cliente ->
                        assertThat(definidos)
                                .withFailMessage("""
                                        %s: el perfil '%s' agrupa roles de '%s', un cliente que ese \
                                        fichero no define. Keycloak aborta la importacion con "App \
                                        doesn't exist in role definitions" y el realm se queda sin \
                                        crear. Lo que cruza aplicaciones va en una importacion \
                                        parcial aparte, aplicada cuando el otro cliente ya existe.""",
                                        fichero, perfil.path("name").asText(), cliente)
                                .contains(cliente));
            }
        }
    }

    @Test
    @DisplayName("el mto-ops que cruza servicios conserva los permisos de mto-configuration")
    void elMtoOpsQueCruzaServiciosConservaLosPermisosDeConfiguration() {
        JsonNode cruzado = leer("mto-ops-cross-service.json");

        JsonNode compuesto = null;
        for (JsonNode perfil : cruzado.path("roles").path("realm")) {
            if ("mto-ops".equals(perfil.path("name").asText())) {
                compuesto = perfil.path("composites").path("client");
            }
        }

        assertThat(compuesto).as("mto-ops-cross-service.json debe redefinir mto-ops").isNotNull();

        // Una importacion parcial reescribe el rol entero. Si este fichero olvidara los permisos de
        // mto-configuration-api, aplicarlo dejaria al perfil de explotacion sin el Actuator de esta
        // aplicacion — justo lo que mto-stock evito no metiendo mto-ops en su importacion parcial.
        assertThat(lista(compuesto.path(API)))
                .withFailMessage("""
                        mto-ops-cross-service.json no conserva los permisos que mto-realm.json da a \
                        mto-ops sobre %s. Al aplicarse reescribiria el perfil entero y se los \
                        llevaria por delante.
                          en mto-realm.json:              %s
                          en mto-ops-cross-service.json:  %s""",
                        API, perfiles(referencia).get("mto-ops"), lista(compuesto.path(API)))
                .containsAll(perfiles(referencia).get("mto-ops"));

        assertThat(compuesto.has("mto-stock-api"))
                .as("mto-ops-cross-service.json existe para cubrir tambien el Actuator de mto-stock")
                .isTrue();
    }

    @Test
    @DisplayName("solo el realm local trae usuarios y secretos")
    void soloElRealmLocalTraeUsuariosYSecretos() {
        assertThat(local.has("users")).as("el realm local trae usuarios de desarrollo").isTrue();

        assertThat(referencia.has("users"))
                .withFailMessage("mto-realm.json ha ganado usuarios: es la definicion que se lleva "
                        + "a un entorno desplegado y no debe traer ninguno.")
                .isFalse();

        for (JsonNode cliente : referencia.path("clients")) {
            assertThat(cliente.has("secret"))
                    .withFailMessage("""
                            El cliente '%s' de mto-realm.json trae un secreto. Los de un entorno \
                            desplegado los genera Keycloak al importar y se leen de su consola; \
                            versionar uno lo publica en el repositorio.""",
                            cliente.path("clientId").asText())
                    .isFalse();
        }
    }

    /** Los permisos: roles de cliente de la API, que es lo que comprueba el código. */
    private static Set<String> permisos(JsonNode realm) {
        return new TreeSet<>(lista(realm.path("roles").path("client").path(API), "name"));
    }

    /** Los perfiles: roles de realm compuestos, con los permisos de la API que agrupa cada uno. */
    private static Map<String, List<String>> perfiles(JsonNode realm) {
        Map<String, List<String>> porNombre = new TreeMap<>();

        for (JsonNode perfil : realm.path("roles").path("realm")) {
            porNombre.put(perfil.path("name").asText(),
                    lista(perfil.path("composites").path("client").path(API)));
        }

        return porNombre;
    }

    private static List<String> lista(JsonNode array) {
        List<String> valores = new ArrayList<>();
        array.forEach(elemento -> valores.add(elemento.asText()));
        return valores;
    }

    private static List<String> lista(JsonNode array, String campo) {
        List<String> valores = new ArrayList<>();
        array.forEach(elemento -> valores.add(elemento.path(campo).asText()));
        return valores;
    }

    private static JsonNode leer(String fichero) {
        Path ruta = DIRECTORIO.resolve(fichero);
        try {
            return JSON.readTree(Files.readString(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta.toAbsolutePath(), e);
        }
    }
}
