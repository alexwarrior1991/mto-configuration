package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportReport;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.FoundationTypeRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.SectioningRepository;
import com.alejandro.mtoconfiguration.support.PostgresTestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Importa el catalogo maestro REAL contra una base de datos real.
 *
 * <p>Los tests unitarios comprueban las reglas con dobles; este comprueba que el fichero
 * que de verdad se va a cargar entra sin pelearse con el esquema. Es donde saldrian a la
 * luz las dos cosas que el resto no puede ver: un codigo mas largo de lo que admite la
 * columna, y la relacion obligatoria de Foundation hacia FoundationType.
 *
 * <p>La segunda pasada es la que justifica el indice unico de V9: si el upsert no fuese
 * idempotente, reimportar duplicaria el catalogo entero.
 */
@SpringBootTest
@DisplayName("Importacion del catalogo maestro real")
class LovMasterImportIT {

    private static final Path MASTER = Path.of("data", "lov-master.xlsx");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerProperties(registry);
        // La siembra automatica se apaga: este test controla cuando se importa.
        registry.add("app.lov.seed-on-startup", () -> "false");
    }

    @Autowired
    private LovMasterImporter importer;
    @Autowired
    private SectioningRepository sectioningRepository;
    @Autowired
    private FoundationRepository foundationRepository;
    @Autowired
    private FoundationTypeRepository foundationTypeRepository;

    @Test
    @DisplayName("carga el maestro y reimportarlo no crea nada nuevo")
    void importaYEsIdempotente() throws IOException {
        assumeThat(Files.isReadable(MASTER))
                .as("data/lov-master.xlsx tiene que estar generado")
                .isTrue();

        LovImportReport first = importMaster();

        assertThat(first.getCreated())
                .as("la primera pasada tiene que cargar el catalogo")
                .isPositive();
        assertThat(first.getFailed())
                .as("ninguna fila habilitada deberia fallar: %s", first.getErrors())
                .isZero();

        // Sectioning es el caso que justifica usar el Legend: el BOQ no trae ni una fila.
        assertThat(sectioningRepository.count()).isPositive();

        // Los catalogos *Type tienen que existir ANTES que las LOV que los referencian,
        // porque la relacion es obligatoria.
        assertThat(foundationTypeRepository.count()).isPositive();
        assertThat(foundationRepository.count()).isPositive();

        long foundationsAfterFirst = foundationRepository.count();

        LovImportReport second = importMaster();

        assertThat(second.getCreated())
                .as("reimportar el mismo fichero no puede crear nada")
                .isZero();
        assertThat(second.getUpdated())
                .as("y tampoco deberia tener que modificar nada")
                .isZero();
        assertThat(foundationRepository.count())
                .as("el catalogo no puede duplicarse: para eso esta el indice unico de V9")
                .isEqualTo(foundationsAfterFirst);
    }

    @Test
    @DisplayName("la simulacion no escribe nada")
    void laSimulacionNoEscribe() throws IOException {
        assumeThat(Files.isReadable(MASTER)).isTrue();

        long before = sectioningRepository.count();

        LovImportReport report = importMasterDryRun();

        assertThat(report.dryRun()).isTrue();
        assertThat(sectioningRepository.count())
                .as("dryRun no puede tocar la base de datos")
                .isEqualTo(before);
    }

    private LovImportReport importMaster() throws IOException {
        try (InputStream in = Files.newInputStream(MASTER)) {
            return importer.importFrom(in, false);
        }
    }

    private LovImportReport importMasterDryRun() throws IOException {
        try (InputStream in = Files.newInputStream(MASTER)) {
            return importer.importFrom(in, true);
        }
    }
}
