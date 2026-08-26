package com.alejandro.mtoconfiguration.configuration.security;

import com.alejandro.mtoconfiguration.configuration.JacksonConfig;
import com.alejandro.mtoconfiguration.controller.commons.ConfigurationApiPaths;
import com.alejandro.mtoconfiguration.core.exception.web.ApiErrorConfiguration;
import com.alejandro.mtoconfiguration.core.exception.web.ErrorCatalog;
import com.alejandro.mtoconfiguration.core.exception.web.ProblemDetailFactory;
import com.alejandro.mtoconfiguration.controller.synchronous.lov.commons.AbstractLovController;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.service.lov.commons.LovCrudService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Antes de estas reglas, cualquier token válido del realm podía borrar cualquier dato maestro: la
 * cadena solo exigía estar autenticado. Lo que se fija aquí es que cada verbo pida su permiso, y
 * sobre todo que los permisos no se impliquen entre sí — poder escribir no debe bastar para borrar
 * ni para cargar en masa.
 * <p>
 * Se prueba contra controladores sonda montados en rutas con la misma forma que las reales, no
 * contra los controladores de negocio: lo que se está verificando son los patrones de la cadena de
 * filtros, y arrastrar la capa de servicio solo añadiría ruido y fragilidad.
 */
@WebMvcTest(controllers = ApiAuthorizationRulesTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfiguration.class, KeycloakJwtAuthenticationConverter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        // El slice de MVC recoge el @ControllerAdvice de errores, que necesita sus colaboradores.
        // Tenerlo delante es además lo fiel: es quien traduce el 403 de @PreAuthorize.
        ProblemDetailFactory.class, ErrorCatalog.class, ApiErrorConfiguration.class,
        // Los handlers de 401/403 serializan con el ObjectMapper de Jackson 2 que declara
        // JacksonConfig; el resto de la aplicación va con Jackson 3, que es el que trae Boot 4.
        JacksonConfig.class,
        ApiAuthorizationRulesTest.ProbeController.class,
        ApiAuthorizationRulesTest.ProbeAsyncController.class,
        ApiAuthorizationRulesTest.ProbeLovController.class})
@TestPropertySource(properties = {
        "app.security.client-id=mto-configuration-api",
        "app.security.principal-claim=preferred_username",
        "app.security.audience-validation-enabled=false",
        "app.security.expose-api-docs=false",
        "app.security.cors.allowed-origins=http://localhost:4200",
        "app.security.cors.allowed-methods=GET,POST,PUT,DELETE",
        "app.security.cors.allowed-headers=Authorization,Content-Type",
        "app.security.cors.allow-credentials=false",
        "app.security.cors.max-age=3600",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8082/realms/mto"
})
class ApiAuthorizationRulesTest {

    private static final String PROBES = ConfigurationApiPaths.BASE_PATH + "/probes";
    private static final String ASYNC_PROBES = ConfigurationApiPaths.ASYNC_BASE_PATH + "/probes";
    private static final String LOVS = ConfigurationApiPaths.BASE_PATH + "/probe-lovs";

    /**
     * Se deja el {@code JwtDecoder} real, sin sustituir por un doble: así el contexto ejercita el
     * cableado del bean —que depende de {@code OAuth2ResourceServerProperties}— y no solo las
     * reglas. No toca la red porque el JWK Set se descarga de forma perezosa, y {@code jwt()}
     * inyecta la autenticación ya resuelta, de modo que nunca llega a decodificar nada.
     */
    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("el contexto publica el decodificador construido por la configuración")
    void elContextoPublicaElDecodificador() {
        assertThat(jwtDecoder).isNotNull();
    }

    @Nested
    @DisplayName("sin token")
    class SinToken {

        @Test
        @DisplayName("una lectura devuelve 401, no 403")
        void unaLecturaDevuelve401() throws Exception {
            mockMvc.perform(get(PROBES + "/1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un borrado devuelve 401")
        void unBorradoDevuelve401() throws Exception {
            mockMvc.perform(delete(PROBES + "/1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el health de Actuator sigue abierto")
        void elHealthSigueAbierto() throws Exception {
            // 404 y no 401: la ruta está permitida, simplemente este slice no monta Actuator.
            mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("el resto de Actuator queda cerrado")
        void elRestoDeActuatorQuedaCerrado() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("el outbox de Actuator no es negocio: exige el rol de explotación")
        void elOutboxExigeRolDeExplotacion() throws Exception {
            mockMvc.perform(get("/actuator/outbox")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("con expose-api-docs a false la documentación exige autenticación")
        void laDocumentacionExigeAutenticacion() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("lecturas")
    class Lecturas {

        @Test
        @DisplayName("config-read abre los GET")
        void configReadAbreLosGet() throws Exception {
            mockMvc.perform(get(PROBES + "/1").with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
            mockMvc.perform(get(PROBES + "/paged").with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("leer datos maestros no abre los endpoints de explotación")
        void leerDatosMaestrosNoAbreExplotacion() throws Exception {
            mockMvc.perform(get("/actuator/outbox").with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/actuator/prometheus").with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isForbidden());
            // Con el rol correcto pasa la autorización; el 404 es que este slice no monta Actuator.
            mockMvc.perform(get("/actuator/outbox").with(con(SecurityRoles.OPS_METRICS)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("HEAD cuenta como lectura y no se cuela por el authenticated() final")
        void headCuentaComoLectura() throws Exception {
            mockMvc.perform(head(PROBES + "/1").with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
            mockMvc.perform(head(PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("escribir no da derecho a leer")
        void escribirNoDaDerechoALeer() throws Exception {
            mockMvc.perform(get(PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("las búsquedas por POST cuentan como lectura, no como escritura")
        void lasBusquedasPorPostCuentanComoLectura() throws Exception {
            mockMvc.perform(json(post(PROBES + "/search")).with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
            mockMvc.perform(json(post(PROBES + "/filter")).with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("escrituras")
    class Escrituras {

        @Test
        @DisplayName("config-write permite crear y actualizar")
        void configWritePermiteCrearYActualizar() throws Exception {
            mockMvc.perform(json(post(PROBES)).with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isOk());
            mockMvc.perform(json(put(PROBES + "/1")).with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("leer no da derecho a escribir")
        void leerNoDaDerechoAEscribir() throws Exception {
            mockMvc.perform(json(post(PROBES)).with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("cargas masivas")
    class CargasMasivas {

        @Test
        @DisplayName("config-import permite el alta y la actualización masivas")
        void configImportPermiteLasCargasMasivas() throws Exception {
            mockMvc.perform(json(post(PROBES + "/bulk")).with(con(SecurityRoles.CONFIG_IMPORT)))
                    .andExpect(status().isOk());
            mockMvc.perform(json(put(PROBES + "/bulk")).with(con(SecurityRoles.CONFIG_IMPORT)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("escribir registro a registro no habilita la carga masiva")
        void escribirNoHabilitaLaCargaMasiva() throws Exception {
            mockMvc.perform(json(post(PROBES + "/bulk")).with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(json(put(PROBES + "/bulk")).with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("borrados")
    class Borrados {

        @Test
        @DisplayName("config-delete permite borrar")
        void configDeletePermiteBorrar() throws Exception {
            mockMvc.perform(delete(PROBES + "/1").with(con(SecurityRoles.CONFIG_DELETE)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("escribir no da derecho a borrar")
        void escribirNoDaDerechoABorrar() throws Exception {
            mockMvc.perform(delete(PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
        }
    }

    /**
     * La rama asíncrona cuelga de otro prefijo, y los patrones de {@code PathPattern} no admiten
     * {@code /**} en mitad de la ruta: si alguien añade una regla nueva olvidándose de la variante
     * {@code /async}, esa ruta se queda sin la restricción específica.
     */
    @Nested
    @DisplayName("rama asíncrona")
    class RamaAsincrona {

        @Test
        @DisplayName("las reglas son las mismas que en la rama síncrona")
        void lasReglasSonLasMismas() throws Exception {
            mockMvc.perform(get(ASYNC_PROBES + "/1").with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
            mockMvc.perform(get(ASYNC_PROBES + "/1").with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete(ASYNC_PROBES + "/1").with(con(SecurityRoles.CONFIG_DELETE)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("las búsquedas y las cargas masivas conservan su permiso propio")
        void lasBusquedasYLasCargasMasivasConservanSuPermiso() throws Exception {
            mockMvc.perform(json(post(ASYNC_PROBES + "/search")).with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
            mockMvc.perform(json(post(ASYNC_PROBES + "/bulk")).with(con(SecurityRoles.CONFIG_IMPORT)))
                    .andExpect(status().isOk());
            mockMvc.perform(json(post(ASYNC_PROBES + "/bulk")).with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("listas de valores")
    class ListasDeValores {

        @Test
        @DisplayName("leer una LOV solo exige config-read")
        void leerUnaLovSoloExigeConfigRead() throws Exception {
            mockMvc.perform(get(LOVS).with(con(SecurityRoles.CONFIG_READ)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("escribir en una LOV exige además lov-manage")
        void escribirEnUnaLovExigeLovManage() throws Exception {
            mockMvc.perform(json(post(LOVS)).with(con(SecurityRoles.CONFIG_WRITE)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(json(post(LOVS)).with(con(SecurityRoles.CONFIG_WRITE, SecurityRoles.LOV_MANAGE)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("borrar una LOV exige config-delete y lov-manage")
        void borrarUnaLovExigeAmbosPermisos() throws Exception {
            mockMvc.perform(delete(LOVS + "/1").with(con(SecurityRoles.CONFIG_DELETE)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(delete(LOVS + "/1").with(con(SecurityRoles.CONFIG_DELETE, SecurityRoles.LOV_MANAGE)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("lov-manage por sí solo no abre nada: la regla del verbo sigue aplicando")
        void lovManagePorSiSoloNoAbreNada() throws Exception {
            mockMvc.perform(json(post(LOVS)).with(con(SecurityRoles.LOV_MANAGE)))
                    .andExpect(status().isForbidden());
        }
    }

    private static RequestPostProcessor con(String... roles) {
        String[] autoridades = java.util.Arrays.stream(roles).map(rol -> "ROLE_" + rol).toArray(String[]::new);
        return jwt().authorities(AuthorityUtils.createAuthorityList(autoridades));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @RestController
    @RequestMapping(ConfigurationApiPaths.BASE_PATH + "/probes")
    static class ProbeController {

        @GetMapping("/{id}")
        String getById(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/paged")
        String paged() {
            return "ok";
        }

        @PostMapping
        String create(@RequestBody String body) {
            return "ok";
        }

        @PostMapping("/bulk")
        String bulkCreate(@RequestBody String body) {
            return "ok";
        }

        @PutMapping("/{id}")
        String update(@PathVariable Long id, @RequestBody String body) {
            return "ok";
        }

        @PutMapping("/bulk")
        String bulkUpdate(@RequestBody String body) {
            return "ok";
        }

        @PostMapping("/search")
        String search(@RequestBody String body) {
            return "ok";
        }

        @PostMapping("/filter")
        String filter(@RequestBody String body) {
            return "ok";
        }

        @DeleteMapping("/{id}")
        String delete(@PathVariable Long id) {
            return "ok";
        }
    }

    @RestController
    @RequestMapping(ConfigurationApiPaths.ASYNC_BASE_PATH + "/probes")
    static class ProbeAsyncController {

        @GetMapping("/{id}")
        String getById(@PathVariable Long id) {
            return "ok";
        }

        @PostMapping("/search")
        String search(@RequestBody String body) {
            return "ok";
        }

        @PostMapping("/bulk")
        String bulkCreate(@RequestBody String body) {
            return "ok";
        }

        @DeleteMapping("/{id}")
        String delete(@PathVariable Long id) {
            return "ok";
        }
    }

    /** Hereda de {@link AbstractLovController} para ejercitar sus {@code @PreAuthorize} reales. */
    @RestController
    @RequestMapping(ConfigurationApiPaths.BASE_PATH + "/probe-lovs")
    static class ProbeLovController extends AbstractLovController<LovDTO> {

        private final LovCrudService<LovDTO> service = new LovCrudService<>() {
            @Override
            public LovDTO findById(Long id) {
                return new LovDTO();
            }

            @Override
            public LovDTO findByCode(String code) {
                return new LovDTO();
            }

            @Override
            public List<LovDTO> findAll() {
                return List.of();
            }

            @Override
            public LovDTO create(LovDTO dto) {
                return dto;
            }

            @Override
            public LovDTO update(Long id, LovDTO dto) {
                return dto;
            }

            @Override
            public void delete(Long id) {
                // sonda: nada que borrar
            }

            @Override
            public List<LovDTO> bulkCreate(List<LovDTO> dtos) {
                return dtos;
            }

            @Override
            public List<LovDTO> bulkUpdate(List<LovDTO> dtos) {
                return dtos;
            }
        };

        @Override
        protected LovCrudService<LovDTO> getService() {
            return service;
        }

        @Override
        protected String getResourceName() {
            return "Probe";
        }
    }
}
