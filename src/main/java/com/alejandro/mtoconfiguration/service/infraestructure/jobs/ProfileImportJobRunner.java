package com.alejandro.mtoconfiguration.service.infraestructure.jobs;

import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileImportReport;
import com.alejandro.mtoconfiguration.service.infraestructure.imports.ProfileMasterImporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Ejecuta una importacion del maestro de perfiles y deja el informe en disco.
 *
 * <p>Gemelo de {@code LovImportJobRunner}, con el mismo criterio: el informe se escribe
 * <b>siempre</b>, tambien en simulacion. Es justo el caso en el que mas se necesita,
 * porque es lo que se revisa antes de decidir si se aplica la carga.
 */
@Slf4j
@Component
public class ProfileImportJobRunner {

    static final String REPORT_PREFIX = "profile-import-";
    static final String REPORT_SUFFIX = ".json";

    private final ProfileMasterImporter importer;
    private final AsyncJobProperties properties;
    private final ObjectMapper objectMapper;

    public ProfileImportJobRunner(ProfileMasterImporter importer, AsyncJobProperties properties,
                                  ObjectMapper objectMapper) {
        this.importer = importer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    void run(UUID jobId, byte[] content, boolean dryRun, ProfileJobProgress progress) {
        ProfileImportReport report = importer.importFrom(
                new ByteArrayInputStream(content), dryRun,
                ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        progress.itemSucceeded();
                    } else {
                        progress.itemFailed(progress.getProcessedItems(),
                                dryRun ? "dry-run" : "import", "PROFILE-IMPORT",
                                "fila rechazada; ver el informe del trabajo");
                    }
                });

        writeReport(jobId, report, progress);
    }

    /**
     * El informe es accesorio respecto al trabajo: si no se puede escribir se avisa, pero
     * no se tumba una importacion que ya ha hecho lo suyo en base de datos.
     */
    private void writeReport(UUID jobId, ProfileImportReport report, ProfileJobProgress progress) {
        String fileName = REPORT_PREFIX + jobId + REPORT_SUFFIX;
        try {
            Path directory = properties.getProfile().getImportReportDirectory();
            Files.createDirectories(directory);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(directory.resolve(fileName).toFile(), report);
            progress.setOutputFileName(fileName);
        } catch (IOException e) {
            log.warn("No se ha podido escribir el informe de la importacion jobId={}", jobId, e);
        }
    }
}
