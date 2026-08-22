package com.alejandro.mtoconfiguration.validator.commons;

import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.utils.Utils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
