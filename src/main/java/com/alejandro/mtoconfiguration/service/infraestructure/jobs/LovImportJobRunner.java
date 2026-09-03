package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportReport;
import com.alejandro.mtoconfiguration.service.lov.imports.LovMasterImporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Ejecuta una importacion del maestro y deja el informe en disco para descargarlo.
 *
 * <p>El informe se escribe siempre, tambien en simulacion: es justo el caso en el que
 * mas se necesita, porque es lo que se revisa antes de decidir si se aplica la carga.
 */
@Slf4j
@Component
public class LovImportJobRunner {

    private static final String REPORT_PREFIX = "lov-import-";
    private static final String REPORT_SUFFIX = ".json";

    private final LovMasterImporter importer;
    private final AsyncJobProperties properties;
    private final ObjectMapper objectMapper;

    public LovImportJobRunner(LovMasterImporter importer, AsyncJobProperties properties,
                              ObjectMapper objectMapper) {
        this.importer = importer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    void run(UUID jobId, byte[] content, boolean dryRun, ProfileJobProgress progress) {
        LovImportReport report = importer.importFrom(
                new ByteArrayInputStream(content), dryRun,
                ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        progress.itemSucceeded();
                    } else {
                        progress.itemFailed(progress.getProcessedItems(), dryRun ? "dry-run" : "import",
                                "LOV-IMPORT", "fila rechazada; ver el informe del trabajo");
                    }
                });

        writeReport(jobId, report, progress);
    }

    /**
     * El informe es accesorio respecto al trabajo: si no se puede escribir se avisa, pero
     * no se tumba una importacion que ya ha hecho su trabajo en base de datos.
     */
    private void writeReport(UUID jobId, LovImportReport report, ProfileJobProgress progress) {
        String fileName = REPORT_PREFIX + jobId + REPORT_SUFFIX;
        try {
            Path directory = properties.getLov().getReportDirectory();
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), report);
            progress.setOutputFileName(fileName);
        } catch (IOException e) {
            log.warn("No se ha podido escribir el informe de la importacion jobId={}", jobId, e);
        }
    }
}
