package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovMasterRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Importa el catalogo maestro completo: lee el fichero, agrupa por entidad y delega
 * en {@link LovUpsertService}.
 *
 * <p>Es el punto unico por el que pasan las dos vias de carga —la siembra al arrancar
 * y el trabajo asincrono del endpoint—, de modo que ambas se comportan igual.
 */
@Service
public class LovMasterImporter {

    private static final Logger log = LoggerFactory.getLogger(LovMasterImporter.class);

    private final LovMasterParser parser;
    private final LovImportRegistry registry;
    private final LovUpsertService upsertService;

    public LovMasterImporter(LovMasterParser parser, LovImportRegistry registry,
                             LovUpsertService upsertService) {
        this.parser = parser;
        this.registry = registry;
        this.upsertService = upsertService;
    }

    public LovImportReport importFrom(InputStream inputStream, boolean dryRun) {
        return importFrom(inputStream, dryRun, ok -> { });
    }

    /**
     * @param progress se invoca una vez por fila procesada, con {@code true} si fue bien.
     *                 Lo usa el trabajo asincrono para ir publicando avance.
     */
    public LovImportReport importFrom(InputStream inputStream, boolean dryRun, Consumer<Boolean> progress) {
        LovMasterParser.LovMasterContent content = parser.parseAll(inputStream);
        LovImportReport report = new LovImportReport(dryRun);

        // Los catalogos *Type van antes: Foundation, Portal y AnchorageFoundation
        // tienen una relacion obligatoria hacia ellos y fallarian todas si no existen.
        process(content.types(), dryRun, report, progress);
        process(content.lovs(), dryRun, report, progress);

        log.info("Importacion de LOV {}: {} altas, {} modificaciones, {} sin cambios, "
                        + "{} omitidas por ENABLED=NO, {} errores",
                dryRun ? "simulada" : "aplicada",
                report.getCreated(), report.getUpdated(), report.getUnchanged(),
                report.getSkippedDisabled(), report.getFailed());

        return report;
    }

    private void process(List<LovMasterRow> rows, boolean dryRun,
                         LovImportReport report, Consumer<Boolean> progress) {
        Map<String, List<LovMasterRow>> byEntity = groupEnabledByEntity(rows, report);

        for (String entityName : registry.supportedEntities()) {
            List<LovMasterRow> entityRows = byEntity.remove(entityName);
            if (entityRows == null || entityRows.isEmpty()) {
                continue;
            }
            registry.find(entityName).ifPresent(target ->
                    upsertService.upsertAll(cast(target), entityRows, dryRun, report, progress));
        }

        // Lo que queda son entidades que el maestro nombra y el importador no conoce.
        // Se reportan una vez por entidad en lugar de una por fila.
        byEntity.forEach((entityName, entityRows) -> {
            LovMasterRow first = entityRows.getFirst();
            report.addError(first.sourceRow(), entityName, first.code(),
                    "entidad LOV no soportada por el importador; soportadas: "
                            + registry.supportedEntities());
            entityRows.forEach(row -> progress.accept(false));
        });
    }

    /**
     * Solo se cargan las filas con ENABLED=SI. El resto son candidatas que salieron del
     * generador marcadas para revision y que nadie ha aceptado todavia.
     */
    private Map<String, List<LovMasterRow>> groupEnabledByEntity(List<LovMasterRow> rows,
                                                                LovImportReport report) {
        return rows.stream()
                .filter(row -> {
                    if (row.enabled()) {
                        return true;
                    }
                    report.skipDisabled();
                    return false;
                })
                .collect(Collectors.groupingBy(LovMasterRow::entity, LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * El registro guarda objetivos con comodines porque cada entidad tiene su propio par
     * DTO/entidad. La conversion es segura: {@link LovImportTarget} construye su DTO con
     * la factoria que se le paso, asi que los tipos casan siempre dentro de un objetivo.
     */
    @SuppressWarnings("unchecked")
    private LovImportTarget<com.alejandro.mtoconfiguration.model.commons.LovDTO,
            com.alejandro.mtoconfiguration.entity.lov.commons.Lov> cast(LovImportTarget<?, ?> target) {
        return (LovImportTarget<com.alejandro.mtoconfiguration.model.commons.LovDTO,
                com.alejandro.mtoconfiguration.entity.lov.commons.Lov>) target;
    }
}
