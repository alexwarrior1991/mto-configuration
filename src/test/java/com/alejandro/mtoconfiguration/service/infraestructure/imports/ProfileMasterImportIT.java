package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.lov.imports.LovMasterImporter;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Importa el maestro de perfiles REAL contra una base de datos real.
 *
 * <p>Los tests unitarios comprueban las reglas con dobles; este comprueba que el fichero
 * que de verdad se va a cargar entra sin pelearse con el esquema. Es donde saldrian a la
 * luz las cosas que ningun doble puede ver: una medida que no cabe en su columna, un KP
 * que se sale de {@code NUMERIC(12,3)}, una LOV que el catalogo no tiene.
 *
 * <p>La segunda pasada es la que justifica los indices unicos de V12: sin ellos el upsert
 * no seria idempotente y reimportar duplicaria 11.715 perfiles.
 */
@SpringBootTest
@DisplayName("Importacion del maestro de perfiles real")
class ProfileMasterImportIT {

    private static final Path PROFILE_MASTER = Path.of("data", "profile-master.xlsx");
    private static final Path LOV_MASTER = Path.of("data", "lov-master.xlsx");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    /**
     * Las tablas que toca la importacion, de la hoja a la raiz.
     *
     * <p>Se vacian al terminar porque aqui las altas se CONFIRMAN de verdad: dejarlas
     * puestas romperia a cualquier otro test que diera de alta una via o un perfil con un
     * nombre del maestro, ya que desde V12 hay indices UNIQUE sobre sus claves naturales.
     *
     * <p>El orden importa aunque haya {@code cascade}: {@code reservation} y
     * {@code stock_movement} de mto-stock no existen aqui, pero disconnector si referencia
     * a profile.
     */
    private static final List<String> TABLES = List.of(
            "steady_arm", "cantilever", "disconnector", "profile", "track",
            "section_insulator", "station", "execution_package");

    /** Se siembran para que los paquetes puedan resolver su empresa; se limpian igual. */
    private static final List<String> COMPANY_TABLES = List.of("business_entity", "comercial_entity_type");

    private static final List<String> LOV_TABLES = List.of(
            "foundation", "foundation_type", "portal", "portal_type",
            "anchorage", "anchorage_foundation", "anchorage_foundation_type",
            "pole_type", "support_type", "cantilever_type", "steady_arm_type",
            "return_support", "disconnector_function", "sectioning");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProfileMasterImporter importer;
    @Autowired
    private ProfileMasterParser parser;
    @Autowired
    private LovMasterImporter lovImporter;
    @Autowired
    private ExecutionPackageRepository executionPackageRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private ProfileRepository profileRepository;

    @AfterEach
    void limpia() {
        String tables = Stream.of(TABLES, LOV_TABLES, COMPANY_TABLES).flatMap(List::stream)
                .flatMap(table -> Stream.of(table, table + "_aud"))
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("truncate table " + tables + " restart identity cascade");
    }

    @Test
    @DisplayName("carga el maestro y reimportarlo no crea nada nuevo")
    void importaYEsIdempotente() throws IOException {
        prepara();

        ProfileImportReport first = importMaster(false);

        assertThat(first.getCreated())
                .as("la primera pasada tiene que cargar el maestro")
                .isPositive();
        assertThat(executionPackageRepository.count()).isPositive();
        assertThat(trackRepository.count()).isPositive();
        assertThat(profileRepository.count()).isPositive();

        long profilesAfterFirst = profileRepository.count();
        long tracksAfterFirst = trackRepository.count();

        ProfileImportReport second = importMaster(false);

        assertThat(second.getCreated())
                .as("reimportar el mismo fichero no puede crear nada")
                .isZero();
        assertThat(profileRepository.count())
                .as("los perfiles no pueden duplicarse: para eso estan los indices unicos de V12")
                .isEqualTo(profilesAfterFirst);
        assertThat(trackRepository.count()).isEqualTo(tracksAfterFirst);
    }

    @Test
    @DisplayName("ningun perfil cargado se queda sin estado")
    void todoPerfilTieneEstado() throws IOException {
        // Es el fallo silencioso que tapa V12: sin las tres filas de profile_status, el
        // codigo se resuelve a null sin quejarse y el perfil entra con la FK vacia.
        prepara();
        importMaster(false);

        Integer sinEstado = jdbcTemplate.queryForObject(
                "select count(*) from profile where profile_status_id is null", Integer.class);

        assertThat(sinEstado).isZero();
    }

    @Test
    @DisplayName("una hoja partida en dos tramos da dos vias distintas")
    void hojaConDosTramos() throws IOException {
        // EP9A / HR Track 1 lleva dos tramos concatenados con 47 codigos de perfil
        // repetidos. Sin el corte de topology.yml chocarian por (via, profileId).
        prepara();
        importMaster(false);

        Integer duplicados = jdbcTemplate.queryForObject("""
                select count(*) from (
                    select track_id, upper(profile_id)
                    from profile where deleted = false
                    group by 1, 2 having count(*) > 1
                ) repetidos
                """, Integer.class);

        assertThat(duplicados)
                .as("ningun identificador de perfil puede repetirse dentro de una via")
                .isZero();
    }

    @Test
    @DisplayName("la simulacion no escribe nada")
    void laSimulacionNoEscribe() throws IOException {
        prepara();

        ProfileImportReport report = importMaster(true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.getCreated())
                .as("la simulacion recorre lo mismo y cuenta igual")
                .isPositive();
        assertThat(profileRepository.count())
                .as("dryRun no puede tocar la base de datos")
                .isZero();
        assertThat(executionPackageRepository.count()).isZero();
    }

    /**
     * Deja el entorno listo, o salta el test si el maestro todavia es un borrador.
     *
     * <p>Los metadatos de los paquetes no estan en los workbooks: los declara una persona en
     * {@code data/tools/topology.yml}. Mientras esa declaracion no este completa, el maestro
     * trae marcadores de relleno y {@code ExecutionPackageValidator} rechaza los once
     * paquetes, con lo que no se carga NADA. Saltar es lo honesto: el test no puede
     * comprobar una carga que aun no se puede hacer, y fallar solo diria que el fichero de
     * declaracion sigue a medias, que ya se sabe.
     */
    private void prepara() throws IOException {
        assumeThat(Files.isReadable(PROFILE_MASTER))
                .as("data/profile-master.xlsx tiene que estar generado")
                .isTrue();

        List<String> companies = declaredCompanies();
        assumeThat(companies)
                .as("topology.yml todavia es un borrador: los paquetes no declaran empresa "
                        + "(company_identification_number), asi que el maestro no se puede cargar")
                .isNotEmpty();

        seedCompanies(companies);

        // El catalogo va PRIMERO: los perfiles referencian sus codigos, y una LOV que no
        // esta se resuelve a null en silencio.
        importLovMaster();
    }

    /** Los NIF que declara el maestro, que son los que el importador va a buscar. */
    private List<String> declaredCompanies() throws IOException {
        try (InputStream in = Files.newInputStream(PROFILE_MASTER)) {
            return parser.parseAll(in).executionPackages().stream()
                    .filter(ExecutionPackageMasterRow::enabled)
                    .map(ExecutionPackageMasterRow::companyIdentificationNumber)
                    .filter(nif -> nif != null && !nif.isBlank())
                    .distinct()
                    .toList();
        }
    }

    /**
     * Las empresas vienen de un maestro externo y no se dan de alta aqui, asi que en una base
     * limpia no existen y el paquete se quedaria sin {@code companyId}. Se siembran con SQL
     * para que el test pruebe la importacion y no la ausencia de datos de referencia.
     */
    private void seedCompanies(List<String> identificationNumbers) {
        jdbcTemplate.update("""
                insert into comercial_entity_type (id, code, description, enabled,
                        create_date, create_user, version_date, version_user, version_number)
                values (-1, 'IT-CET', 'Tipo de entidad para el test', true,
                        now(), 'test', now(), 'test', 1)
                on conflict (id) do nothing
                """);

        for (int index = 0; index < identificationNumbers.size(); index++) {
            jdbcTemplate.update("""
                    insert into business_entity (id, name, code, identification_number,
                            comercial_entity_type_id, deleted,
                            create_date, create_user, version_date, version_user, version_number)
                    values (?, ?, ?, ?, -1, false, now(), 'test', now(), 'test', 1)
                    on conflict (id) do nothing
                    """, -(index + 1L), "Empresa " + (index + 1), "IT-" + (index + 1),
                    identificationNumbers.get(index));
        }
    }

    private ProfileImportReport importMaster(boolean dryRun) throws IOException {
        try (InputStream in = Files.newInputStream(PROFILE_MASTER)) {
            return importer.importFrom(in, dryRun);
        }
    }

    private void importLovMaster() throws IOException {
        assumeThat(Files.isReadable(LOV_MASTER))
                .as("data/lov-master.xlsx tiene que estar generado")
                .isTrue();
        try (InputStream in = Files.newInputStream(LOV_MASTER)) {
            lovImporter.importFrom(in, false);
        }
    }
}
