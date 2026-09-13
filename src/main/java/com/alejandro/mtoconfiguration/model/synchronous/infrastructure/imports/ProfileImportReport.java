package com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado de una importacion del maestro de perfiles.
 *
 * <p>Hermano de {@code LovImportReport} y con la misma propiedad, que es la que le da
 * valor: <b>el cuerpo es identico en simulacion y en carga real</b>, y lo unico que
 * cambia es {@link #dryRun()}. Asi se puede comparar lo que dijo la simulacion con lo
 * que hizo la carga, que es la unica forma de saber que un {@code dryRun} sirve de algo.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileImportReport {

    /** Nombres de entidad que aparecen en el informe, en el orden en que se cargan. */
    public static final String EXECUTION_PACKAGE = "ExecutionPackage";
    public static final String STATION = "Station";
    public static final String TRACK = "Track";
    public static final String PROFILE = "Profile";

    private final boolean dryRun;
    private final Map<String, EntityOutcome> byEntity = new LinkedHashMap<>();
    private final List<ItemError> errors = new ArrayList<>();
    private int skippedDisabled;
    private int cantileversWritten;

    public ProfileImportReport(boolean dryRun) {
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

    /**
     * Las mensulas no se cuentan como entidad propia porque no se importan sueltas:
     * viajan dentro de su perfil y comparten su transaccion. Contarlas aparte sugeriria
     * que se pueden crear o modificar por su cuenta, que no es el caso.
     */
    public int getCantileversWritten() {
        return cantileversWritten;
    }

    public void skipDisabled() {
        skippedDisabled++;
    }

    public void addCantilevers(int count) {
        cantileversWritten += count;
    }

    public EntityOutcome outcomeOf(String entity) {
        return byEntity.computeIfAbsent(entity, key -> new EntityOutcome());
    }

    public void addError(int row, String entity, String reference, String message) {
        errors.add(new ItemError(row, entity, reference, message));
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

    /** Recuento por entidad. */
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
     * Fallo de una fila concreta.
     *
     * @param row       fila del Excel, para poder ir directamente a ella
     * @param reference clave natural del elemento ({@code EP6 / TRACK 1 / 83-1.02}),
     *                  que es lo que identifica la fila para quien revisa el maestro
     */
    public record ItemError(int row, String entity, String reference, String message) {
    }
}
