package com.alejandro.mtoconfiguration.service.infraestructure;

import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.entity.infrastructure.Disconnector;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.DisconnectorRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import com.alejandro.mtoconfiguration.service.commons.event.WrittenEntitiesCheck;
import com.alejandro.mtoconfiguration.validator.commons.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * La clave natural del seccionador: su nombre, sin mayusculas, dentro de su paquete de ejecucion.
 *
 * <p>Es la del importador del maestro ({@code InfrastructureUpsertService.upsertDisconnector}), que
 * busca el seccionador de una fila con {@code DisconnectorRepository.findByNameInPackage} y, si el
 * nombre esta dos veces en el paquete, no elige: no carga la fila. La API no la miraba, asi que un
 * alta con un nombre repetido entraba y dejaba ese nombre sin poder cargarse nunca mas.
 *
 * <p>No es un indice unico como las demas claves naturales (V12, V28, V31) porque el paquete no es
 * una columna del seccionador: sale de su estacion, de la via de su poste o de su via propia, lo
 * que tenga, y la estacion es opcional. Tampoco es un validador: el seccionador que viaja dentro
 * del {@code PUT} de su estacion no pasa por {@code DisconnectorService}. Se comprueba aqui, sobre
 * cada seccionador que el flush ha escrito, <b>con la misma consulta del importador</b>, de modo
 * que lo que la API rechaza es exactamente lo que el importador no cargaria. Un valor repetido sale
 * como en los demas maestros: 409 {@code BUS-002}.
 *
 * <p>Uno borrado no ocupa su nombre (la consulta no lo ve), y uno que se borra no se comprueba. Lo
 * que no cubre: dos altas simultaneas con el mismo nombre, que no se ven la una a la otra; para eso
 * haria falta guardar el paquete en la fila y un indice sobre el.
 */
@Component
@RequiredArgsConstructor
public class DisconnectorNaturalKeyGuard implements WrittenEntitiesCheck {

    private static final String FIELD_NAME = "name";

    private final DisconnectorRepository disconnectorRepository;

    @Override
    public void afterFlush(List<EntityChangeTracker.Change> written) {
        for (EntityChangeTracker.Change change : written) {
            if (change.operation() != EntityChangeOperation.DELETED
                    && Hibernate.unproxy(change.entity()) instanceof Disconnector disconnector) {
                check(disconnector);
            }
        }
    }

    private void check(Disconnector disconnector) {
        for (Long packageId : packagesOf(disconnector.getId())) {
            boolean repeated = disconnectorRepository.findByNameInPackage(disconnector.getName(), packageId).stream()
                    .anyMatch(other -> !Objects.equals(other.getId(), disconnector.getId()));

            if (repeated) {
                throw new BaseException(Alert.ofDanger(ErrorCodes.DUPLICATED_RESOURCE, FIELD_NAME));
            }
        }
    }

    /** Los paquetes en los que el importador encontraria este seccionador: casi siempre, uno. */
    private Set<Long> packagesOf(Long disconnectorId) {
        Set<Long> packages = new LinkedHashSet<>();

        disconnectorRepository.findPackageIdsById(disconnectorId).ifPresent(ids -> {
            packages.add(ids.getStationPackageId());
            packages.add(ids.getPoleTrackPackageId());
            packages.add(ids.getTrackPackageId());
        });
        packages.remove(null);

        return packages;
    }
}
