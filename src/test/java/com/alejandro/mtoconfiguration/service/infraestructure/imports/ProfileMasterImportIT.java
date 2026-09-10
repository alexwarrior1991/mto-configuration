package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileLovCodes;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.lov.imports.LovMasterImporter;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.assertj.core.api.SoftAssertions;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
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

    /**
     * Las empresas NO se limpian.
     *
     * <p>Desde V19 la migracion siembra Syneox —el NIF que declaran los once paquetes— y
     * el catalogo de tipos de entidad comercial. Vaciar esas tablas al terminar borraria
     * datos que la migracion da por puestos, y el siguiente test que los mirase
     * —{@code FlywayMigrationIT}— fallaria por el orden de ejecucion, no por el esquema.
     */

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
        String tables = Stream.of(TABLES, LOV_TABLES).flatMap(List::stream)
                .flatMap(table -> Stream.of(table, table + "_aud"))
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("truncate table " + tables + " restart identity cascade");
    }

    /**
     * La carga entera, comprobada de una sola pasada.
     *
     * <p>Las cuatro cosas que se miran aqui —que carga, que ningun perfil se queda sin estado,
     * que la clave natural no se repite y que reimportar no crea nada— comparten la misma
     * importacion <b>a proposito</b>. Cada pasada escribe 11.714 perfiles y 14.244 mensulas con
     * una transaccion por elemento; una por asercion eran cuatro pasadas y un cuarto de hora de
     * CI para mirar exactamente las mismas filas. Con {@link SoftAssertions} un fallo no tapa a
     * los otros, que es lo unico que se perdia al juntarlas.
     */
    @Test
    @DisplayName("carga el maestro entero y reimportarlo no crea nada nuevo")
    void importaYEsIdempotente() throws IOException {
        prepara();

        ProfileImportReport first = importMaster(false);

        assertThat(first.getCreated())
                .as("la primera pasada tiene que cargar el maestro: %s", desglose(first))
                .isPositive();

        long profilesAfterFirst = profileRepository.count();
        long tracksAfterFirst = trackRepository.count();

        SoftAssertions comprobaciones = new SoftAssertions();
        // Ninguna fila del maestro puede quedarse por el camino: el maestro se genera ya filtrado
        // (lo que no se puede cargar sale ENABLED=NO y se cuenta aparte), asi que un fallo aqui
        // es un defecto de la carga, no un dato malo.
        comprobaciones.assertThat(first.getFailed())
                .as("ninguna fila del maestro puede fallar: %s", desglose(first))
                .isZero();
        comprobaciones.assertThat(executionPackageRepository.count()).isPositive();
        comprobaciones.assertThat(tracksAfterFirst).isPositive();
        comprobaciones.assertThat(profilesAfterFirst)
                .as("no basta con que entre alguno: tienen que entrar TODOS los cargables, y son "
                        + "los que el maestro marca ENABLED=SI. %s", desglose(first))
                .isEqualTo(perfilesCargables());

        // El fallo silencioso que tapa V12: sin las tres filas de profile_status, el codigo se
        // resuelve a null sin quejarse y el perfil entra con la clave ajena vacia.
        comprobaciones.assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from profile where profile_status_id is null", Integer.class))
                .as("ningun perfil cargado puede quedarse sin estado")
                .isZero();

        // EP9A / HR Track 1 y HR Track 2 llevan dos tramos concatenados, y con ellos 47 y 46
        // codigos de perfil repetidos dentro de la MISMA via: no se parten en dos, van juntos y
        // en orden. Lo que no puede repetirse es la clave natural que fija V18, que incluye el
        // punto kilometrico: dos perfiles con el mismo identificador estan en dos sitios
        // distintos de la via. Comprobar solo (via, identificador) seria comprobar lo contrario
        // de lo que el esquema permite desde V18.
        comprobaciones.assertThat(jdbcTemplate.queryForObject("""
                        select count(*) from (
                            select track_id, upper(profile_id), kilometric_point
                            from profile where deleted = false
                            group by 1, 2, 3 having count(*) > 1
                        ) repetidos
                        """, Integer.class))
                .as("ningun perfil puede repetir identificador Y punto kilometrico en su via")
                .isZero();

        // Las dos relaciones N:M del perfil, contadas contra el maestro. Un perfil puede llevar
        // varios seccionamientos y varios anclajes, y el maestro los escribe separados por '|'.
        //
        // Comprobar solo que el perfil entra no vale: si el importador se quedase con el primer
        // codigo de la celda, o si el mapper reconciliase la coleccion dejando uno, el recuento
        // de perfiles daria exactamente igual y se perderian 850 pertenencias sin un solo error.
        // Por eso se cuentan las FILAS DE LA TABLA DE UNION, que es donde vive el dato.
        comprobaciones.assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from profile_sectioning", Long.class))
                .as("cada seccionamiento del maestro tiene que ser su propia fila")
                .isEqualTo(pertenenciasEsperadas(ProfileLovCodes::sectioning));
        comprobaciones.assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from profile_anchorage", Long.class))
                .as("cada anclaje del maestro tiene que ser su propia fila")
                .isEqualTo(pertenenciasEsperadas(ProfileLovCodes::anchorage));

        // Y que el caso de varios existe de verdad: sin esto, los dos recuentos de arriba
        // cuadrarian igual con un maestro en el que cada perfil llevara un solo codigo, que es
        // justo lo que el modelo N:M vino a arreglar.
        comprobaciones.assertThat(perfilesConVarios("profile_sectioning"))
                .as("el maestro trae perfiles con varios seccionamientos")
                .isPositive();
        comprobaciones.assertThat(perfilesConVarios("profile_anchorage"))
                .as("el maestro trae perfiles con varios anclajes")
                .isPositive();
        comprobaciones.assertAll();

        ProfileImportReport second = importMaster(false);

        SoftAssertions segundaPasada = new SoftAssertions();
        segundaPasada.assertThat(second.getCreated())
                .as("reimportar el mismo fichero no puede crear nada.%n"
                        + "  primera pasada: %s%n"
                        + "  segunda pasada: %s", desglose(first), desglose(second))
                .isZero();
        // Y tampoco puede fallar. Sin esta linea la idempotencia daba verde mientras las
        // 11.714 modificaciones reventaban una por una: crear cero es lo que se esperaba,
        // pero lo estaba consiguiendo por no llegar a escribir nada.
        segundaPasada.assertThat(second.getFailed())
                .as("la segunda pasada tiene que MODIFICAR, no fallar: %s", desglose(second))
                .isZero();
        segundaPasada.assertThat(second.getUpdated())
                .as("y modificar TODO lo que cargo la primera: %s", desglose(second))
                .isEqualTo(first.getCreated());
        segundaPasada.assertAll();
        assertThat(profileRepository.count())
                .as("los perfiles no pueden duplicarse: para eso estan los indices unicos de V12")
                .isEqualTo(profilesAfterFirst);
        assertThat(trackRepository.count()).isEqualTo(tracksAfterFirst);
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
     * Recuento por entidad en una linea, para que un fallo de idempotencia diga QUE se ha
     * creado y no solo cuanto. Con 11.714 perfiles, "esperaba 0 y fue 831" no permite ni
     * empezar a mirar; "Profile creados=831" apunta al sitio.
     */
    private static String desglose(ProfileImportReport report) {
        String entidades = report.getByEntity().entrySet().stream()
                .map(entry -> "%s[+%d ~%d =%d]".formatted(entry.getKey(),
                        entry.getValue().getCreated(), entry.getValue().getUpdated(),
                        entry.getValue().getUnchanged()))
                .collect(Collectors.joining(" "));
        String errores = report.getErrors().stream()
                .limit(3)
                .map(error -> "fila %d %s: %s".formatted(error.row(), error.reference(), error.message()))
                .collect(Collectors.joining(" | "));
        return "%s mensulas=%d saltadas=%d fallidas=%d%s".formatted(
                entidades, report.getCantileversWritten(), report.getSkippedDisabled(),
                report.getFailed(), errores.isEmpty() ? "" : " -> " + errores);
    }

    /**
     * Deja el entorno listo, o salta el test si el maestro todavia es un borrador.
     *
     * <p>Los metadatos de los paquetes no estan en los workbooks: los declara una persona en
     * {@code data/tools/topology.yml}. Mientras esa declaracion no este completa, el maestro
     * trae marcadores de relleno y {@code ExecutionPackageValidator} rechaza los paquetes, con
     * lo que no se carga NADA. Saltar es lo honesto: el test no puede comprobar una carga que
     * aun no se puede hacer, y fallar solo diria que el fichero de declaracion sigue a medias,
     * que ya se sabe.
     *
     * <p>Se exigen TODOS los paquetes, no uno cualquiera. Con un solo paquete declarado los
     * cuatro tests pasarian sin haber cargado los otros diez, y {@code hojaConDosTramos} —que
     * existe para EP9A— daria verde sin que EP9A hubiera entrado. Un verde asi es peor que un
     * salto, porque parece una comprobacion.
     *
     * <p>Y la condicion es la de verdad, no "alguien ha escrito un NIF": el validador tambien
     * exige que la fecha de fin sea posterior a la de inicio, y la plantilla sembrada deja las
     * dos en 1970-01-01. Con solo mirar el NIF, el test se ponia a correr contra un maestro que
     * seguia sin poder cargarse.
     */
    private void prepara() throws IOException {
        assumeThat(Files.isReadable(PROFILE_MASTER))
                .as("data/profile-master.xlsx tiene que estar generado")
                .isTrue();

        List<ExecutionPackageMasterRow> paquetes = enabledPackages();
        assumeThat(paquetes).as("el maestro no trae ningun paquete activo").isNotEmpty();

        List<String> incompletos = paquetes.stream()
                .filter(row -> !esDeclaracionCompleta(row))
                .map(ExecutionPackageMasterRow::code)
                .toList();
        assumeThat(incompletos)
                .as("topology.yml todavia es un borrador: a estos paquetes les falta el NIF de "
                        + "la empresa o la fecha de fin posterior a la de inicio, asi que el "
                        + "maestro no se puede cargar")
                .isEmpty();

        List<String> companies = paquetes.stream()
                .map(ExecutionPackageMasterRow::companyIdentificationNumber)
                .distinct()
                .toList();
        seedCompanies(companies);

        // El catalogo va PRIMERO: los perfiles referencian sus codigos, y una LOV que no
        // esta se resuelve a null en silencio.
        importLovMaster();
    }

    /** Los paquetes cargables que declara el maestro. */
    private List<ExecutionPackageMasterRow> enabledPackages() throws IOException {
        try (InputStream in = Files.newInputStream(PROFILE_MASTER)) {
            return parser.parseAll(in).executionPackages().stream()
                    .filter(ExecutionPackageMasterRow::enabled)
                    .toList();
        }
    }

    /**
     * Cuantos perfiles tiene que cargar el maestro.
     *
     * <p>Se cuenta del fichero, no de una constante: el maestro se regenera y el numero cambia.
     * Y se compara el total, no "que entre alguno": con {@code isPositive()}, 11.091 de los
     * 11.714 perfiles se caian con la misma excepcion sin que ninguna asercion lo dijera. Lo que
     * acabo delatandolo fue la idempotencia, que habla de otra cosa.
     */
    /**
     * Cuantas filas tiene que tener una tabla de union, contadas sobre el maestro.
     *
     * <p>Una celda multivalor del maestro lleva los codigos separados por {@code '|'} —el
     * separador no puede ser el espacio, porque hay codigos del catalogo que lo llevan dentro—,
     * y cada uno es una pertenencia. Se cuentan aqui y no se fija un numero a mano para que el
     * test siga valiendo cuando el maestro se regenere.
     */
    private long pertenenciasEsperadas(Function<ProfileLovCodes, String> campo) throws IOException {
        try (InputStream in = Files.newInputStream(PROFILE_MASTER)) {
            return parser.parseAll(in).profiles().stream()
                    .filter(ProfileMasterRow::enabled)
                    .map(row -> campo.apply(row.lov()))
                    .filter(codes -> codes != null && !codes.isBlank())
                    .flatMap(codes -> Arrays.stream(codes.trim().split("\\|")))
                    .filter(code -> !code.isBlank())
                    .count();
        }
    }

    /** Perfiles con mas de una fila en esa tabla de union. */
    private int perfilesConVarios(String tabla) {
        return jdbcTemplate.queryForObject(
                "select count(*) from (select profile_id from " + tabla
                        + " group by profile_id having count(*) > 1) varios", Integer.class);
    }

    private long perfilesCargables() throws IOException {
        try (InputStream in = Files.newInputStream(PROFILE_MASTER)) {
            return parser.parseAll(in).profiles().stream()
                    .filter(ProfileMasterRow::enabled)
                    .count();
        }
    }

    /** Lo que ExecutionPackageValidator va a exigir, comprobado antes de intentar cargar. */
    private static boolean esDeclaracionCompleta(ExecutionPackageMasterRow row) {
        return row.companyIdentificationNumber() != null
                && !row.companyIdentificationNumber().isBlank()
                && row.startDate() != null
                && row.endDate() != null
                && row.endDate().isAfter(row.startDate());
    }

    /**
     * Las empresas vienen de un maestro externo y no se dan de alta aqui, asi que en una base
     * limpia no existen y el paquete se quedaria sin {@code companyId}. Se siembran con SQL
     * para que el test pruebe la importacion y no la ausencia de datos de referencia.
     */
    /**
     * Rellena las empresas que el maestro declara y la migracion no siembra.
     *
     * <p>Desde V19, el NIF de los once paquetes —B10744258, Syneox— ya viene puesto, asi
     * que en la practica esto no inserta nada: la carga se prueba contra la fila de
     * verdad, que es lo que se quiere. Sigue aqui para que declarar un NIF nuevo en
     * {@code topology.yml} no rompa el test antes de que a nadie le de tiempo a darlo de
     * alta donde toque.
     *
     * <p>El conflicto se resuelve por {@code identification_number} y no por {@code id}:
     * la fila sembrada tiene su propio id, asi que insertar una copia con id negativo no
     * chocaria por id —chocaria por el NIF, que es UNIQUE— y la sentencia moriria.
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
                    on conflict (identification_number) do nothing
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
