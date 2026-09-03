package com.alejandro.mtoconfiguration.model.synchronous.lov.imports;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de una importacion del catalogo maestro.
 *
 * <p>El cuerpo es el mismo tanto en simulacion ({@code dryRun}) como en ejecucion
 * real: lo unico que cambia es {@link #dryRun()}. Asi se puede comparar lo que dijo
 * la simulacion con lo que hizo la carga.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LovImportReport {

    private final boolean dryRun;
    private final Map<String, EntityOutcome> byEntity = new LinkedHashMap<>();
    private final List<ItemError> errors = new ArrayList<>();
    private int skippedDisabled;

    public LovImportReport(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public Map<String, EntityOutcome> getByEntity() {
        return byEntity;
    }

    public List<ItemError> getErrors() {
        return errors;
    }

    public int getSkippedDisabled() {
        return skippedDisabled;
    }

    public void skipDisabled() {
        skippedDisabled++;
    }

    public EntityOutcome outcomeOf(String entity) {
        return byEntity.computeIfAbsent(entity, key -> new EntityOutcome());
    }

    public void addError(int row, String entity, String code, String message) {
        errors.add(new ItemError(row, entity, code, message));
    }

    public int getCreated() {
        return byEntity.values().stream().mapToInt(EntityOutcome::getCreated).sum();
    }

    public int getUpdated() {
        return byEntity.values().stream().mapToInt(EntityOutcome::getUpdated).sum();
    }

    public int getUnchanged() {
        return byEntity.values().stream().mapToInt(EntityOutcome::getUnchanged).sum();
    }

    public int getFailed() {
        return errors.size();
    }

    /** Recuento por entidad LOV. */
    public static class EntityOutcome {
        private int created;
        private int updated;
        private int unchanged;

        public int getCreated() {
            return created;
        }

        public int getUpdated() {
            return updated;
        }

        public int getUnchanged() {
            return unchanged;
        }

        public void create() {
            created++;
        }

        public void update() {
            updated++;
        }

        public void unchanged() {
            unchanged++;
        }
    }

    /**
     * Fallo de una fila concreta. Se guarda la fila del Excel para que quien revise
     * el informe pueda ir directamente a ella.
     */
    public record ItemError(int row, String entity, String code, String message) {
    }
}
