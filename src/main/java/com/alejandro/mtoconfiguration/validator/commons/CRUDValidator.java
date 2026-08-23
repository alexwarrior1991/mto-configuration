package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public abstract class CRUDValidator<T extends BaseDTO> extends GenericValidator<T> {

    public abstract List<Alert> validateBeforeDelete(T dto);

    /**
     * Validación del DTO cuando viaja anidado dentro de su padre y todavía no existe en base de
     * datos. Por defecto es idéntica a la de raíz; las entidades que reciben su clave ajena del
     * padre la relajan (ver {@code NormalEntityValidator#validateParentReferences}).
     */
    public List<Alert> validateBeforeSaveAsChild(T dto) {
        return validateBeforeSave(dto);
    }

    /**
     * Equivalente de {@link #validateBeforeSaveAsChild(BaseDTO)} para un hijo que ya existe.
     */
    public List<Alert> validateBeforeUpdateAsChild(T dto) {
        return validateBeforeUpdate(dto);
    }

    /**
     * Valida una colección de hijos y vuelca las alertas en {@code target} con la ruta del campo
     * prefijada como {@code path[i].campo}.
     *
     * <p>Cada elemento se valida según su propio estado, no según la operación del padre: un hijo
     * sin id se valida como alta y uno con id como modificación. Así se puede añadir un hijo nuevo
     * dentro de la actualización del padre sin que se le exija un id que aún no tiene.</p>
     */
    protected <D extends BaseDTO> void validateChildren(List<Alert> target,
                                                        Collection<D> children,
                                                        CRUDValidator<D> validator,
                                                        String path) {
        if (CollectionUtils.isEmpty(children)) {
            return;
        }

        int index = 0;
        for (D child : children) {
            String childPath = path + "[" + index + "]";

            if (child == null) {
                // Un hueco dentro de la colección es dato malformado, no una asociación ausente.
                target.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, childPath));
            } else {
                validateChild(target, child, validator, childPath);
            }

            index++;
        }
    }

    /**
     * Valida un hijo único (relación uno a uno) prefijando sus campos con {@code path.campo}.
     * Un hijo nulo no genera alerta: en una asociación uno a uno la obligatoriedad se declara
     * aparte, con el resto de campos requeridos del padre.
     */
    protected <D extends BaseDTO> void validateChild(List<Alert> target,
                                                     D child,
                                                     CRUDValidator<D> validator,
                                                     String path) {
        if (child == null) {
            return;
        }

        List<Alert> childAlerts = Utils.isNew(child)
                ? validator.validateBeforeSaveAsChild(child)
                : validator.validateBeforeUpdateAsChild(child);

        target.addAll(prefixFields(childAlerts, path));
    }

    /**
     * Validación de un alta en lote: cada elemento con las mismas reglas que en un alta individual.
     *
     * <p>El comportamiento por defecto <b>valida</b>. Antes era devolver la lista vacía, es decir no
     * validar nada, y bastaba con olvidarse de sobrescribir el método para dejar un endpoint de lote
     * sin ninguna comprobación. Quien necesite reglas adicionales de lote —duplicados dentro del
     * propio envío, por ejemplo— llama a {@code super} y añade las suyas.</p>
     */
    @Override
    public List<Alert> validateBeforeBulkSave(List<T> dtoList) {
        return validateBulk(dtoList, this::validateBeforeSave);
    }

    @Override
    public List<Alert> validateBeforeBulkUpdate(List<T> dtoList) {
        return validateBulk(dtoList, this::validateBeforeUpdate);
    }

    /**
     * Recorre el lote acumulando las alertas de cada elemento con su índice.
     *
     * <p>En un endpoint de lote el cuerpo de la petición <i>es</i> el array, así que la ruta de un
     * campo es {@code [i].campo}: la ruta siempre se expresa desde la raíz del cuerpo, igual que un
     * hijo anidado se expresa {@code tracks[i].campo} porque allí la raíz es el objeto padre.</p>
     */
    protected List<Alert> validateBulk(List<T> dtoList, Function<T, List<Alert>> validator) {
        List<Alert> alerts = new ArrayList<>();

        if (CollectionUtils.isEmpty(dtoList)) {
            alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, getEntityName()));
            return alerts;
        }

        for (int index = 0; index < dtoList.size(); index++) {
            T dto = dtoList.get(index);
            String path = "[" + index + "]";

            if (dto == null) {
                alerts.add(Alert.ofDanger(ErrorCodes.VALIDATION_REQUIRED_FIELD, path));
            } else {
                alerts.addAll(prefixFields(validator.apply(dto), path));
            }
        }

        return alerts;
    }

    /**
     * Antepone {@code path.} a la ruta de las alertas recibidas, de modo que el cliente reciba
     * siempre una ruta JSON navegable ({@code tracks[0].name}, {@code steadyArm.length}).
     *
     * <p>Solo se prefija el primer elemento de {@code fields}: por convención es la ruta del campo
     * y el resto son argumentos del mensaje (mínimo, máximo...). Prefijarlos todos producía rutas
     * sin sentido del tipo {@code tracks[0].200}.</p>
     *
     * <p>Modifica las alertas recibidas y devuelve la misma lista sin los elementos nulos.</p>
     */
    protected static List<Alert> prefixFields(List<Alert> alerts, String path) {
        return CollectionUtils.emptyIfNull(alerts).stream()
                .filter(Objects::nonNull)
                .map(alert -> prefixPath(alert, path))
                .toList();
    }

    private static Alert prefixPath(Alert alert, String path) {
        List<String> fields = alert.getFields();

        if (fields.isEmpty()) {
            alert.setFields(List.of(path));
            return alert;
        }

        List<String> prefixed = new ArrayList<>(fields);
        prefixed.set(0, path + "." + fields.getFirst());
        alert.setFields(prefixed);

        return alert;
    }
}
