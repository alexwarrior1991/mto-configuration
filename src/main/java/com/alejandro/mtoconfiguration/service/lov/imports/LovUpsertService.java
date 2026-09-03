package com.alejandro.mtoconfiguration.service.lov.imports;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovImportReport;
import com.alejandro.mtoconfiguration.model.synchronous.lov.imports.LovMasterRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Carga (o simula la carga de) un bloque de filas del maestro sobre una entidad LOV.
 *
 * <p>Alta o modificacion segun el codigo, que es la clave natural del catalogo. El
 * indice unico que anade {@code V9__widen_lov_code_and_unique.sql} es lo que hace que
 * reimportar el mismo fichero no duplique nada.
 *
 * <p><b>Una transaccion por fila.</b> Igual que {@code ProfileBulkJobRunner}, y al
 * contrario que {@code AbstractLovCrudService.bulkCreate}, que mete el lote entero en
 * una transaccion: alli una sola fila mala tira las 1000, y el informe no puede decir
 * cual fallo. Aqui una fila que falla se anota y el resto sigue.
 *
 * <p>Efecto conocido y aceptado: cada alta o modificacion publica su propio
 * {@code LovCacheEvictionEvent} al confirmar, asi que una carga completa invalida la
 * cache muchas veces en lugar de una. Se asume a proposito. La alternativa —escribir
 * por el repositorio para agrupar el evento— saltaria los ganchos
 * {@code beforeCreate}/{@code beforeUpdate}, que son justo los que resuelven la
 * relacion obligatoria al {@code *Type}, ademas de la validacion y la auditoria de
 * Envers. Es un trabajo de administracion que se ejecuta muy de vez en cuando: el
 * coste de invalidar de mas es irrelevante frente a saltarse esa cadena.
 */
@Service
public class LovUpsertService {

    private static final Logger log = LoggerFactory.getLogger(LovUpsertService.class);

    /**
     * @param dryRun si es cierto no se escribe nada, pero se calcula exactamente el
     *               mismo informe: es lo que permite revisar una carga antes de hacerla
     */
    public <D extends LovDTO, E extends Lov> void upsertAll(
            LovImportTarget<D, E> target,
            List<LovMasterRow> rows,
            boolean dryRun,
            LovImportReport report,
            Consumer<Boolean> progress
    ) {
        Map<String, E> existing = loadExisting(target, rows);
        LovImportReport.EntityOutcome outcome = report.outcomeOf(target.entityName());

        for (LovMasterRow row : rows) {
            try {
                E current = existing.get(row.code());
                if (current == null) {
                    if (!dryRun) {
                        target.service().create(buildDto(target, row));
                    }
                    outcome.create();
                } else if (needsUpdate(current, row)) {
                    if (!dryRun) {
                        target.service().update(current.getId(), buildDto(target, row));
                    }
                    outcome.update();
                } else {
                    outcome.unchanged();
                }
                progress.accept(true);
            } catch (Exception e) {
                // Una fila mala no puede tumbar la importacion: se anota con su numero
                // de fila del Excel para que se pueda ir directamente a corregirla.
                log.warn("Fila {} del maestro ({} / {}): {}",
                        row.sourceRow(), row.entity(), row.code(), e.getMessage());
                report.addError(row.sourceRow(), row.entity(), row.code(), messageOf(e));
                progress.accept(false);
            }
        }
    }

    /**
     * Una sola consulta por entidad. Con {@code findByCode} fila a fila serian ~1000
     * SELECT para un maestro completo.
     */
    private <D extends LovDTO, E extends Lov> Map<String, E> loadExisting(
            LovImportTarget<D, E> target, List<LovMasterRow> rows) {
        List<String> codes = rows.stream().map(LovMasterRow::code).distinct().toList();
        if (codes.isEmpty()) {
            return Map.of();
        }

        Map<String, E> byCode = new HashMap<>();
        for (E entity : target.repository().findByCodeIn(codes)) {
            byCode.put(entity.getCode(), entity);
        }
        return byCode;
    }

    /**
     * Evita reescribir lo que ya esta igual. Sin esto, una reimportacion generaria una
     * revision de Envers y un evento de cache por cada fila del catalogo aunque no
     * hubiese cambiado nada.
     */
    private <E extends Lov> boolean needsUpdate(E current, LovMasterRow row) {
        return !Objects.equals(current.getDescription(), row.description()) || !current.isEnabled();
    }

    private <D extends LovDTO, E extends Lov> D buildDto(LovImportTarget<D, E> target, LovMasterRow row) {
        if (target.requiresType() && !row.hasType()) {
            throw new IllegalArgumentException(
                    target.entityName() + " exige un TIPO y la fila no lo trae");
        }
        return target.newDto(row.code(), row.description(), row.drawingNumber(), row.type());
    }

    private String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
