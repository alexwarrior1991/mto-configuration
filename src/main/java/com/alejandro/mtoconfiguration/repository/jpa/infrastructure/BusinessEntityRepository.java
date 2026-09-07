package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.configuration.BusinessEntity;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessEntityRepository extends CRUDRepository<BusinessEntity> {

    /**
     * Busqueda por el numero de identificacion, que es lo unico UNIQUE de esta tabla
     * ({@code code} no lo es).
     *
     * <p>La usa el importador del maestro de perfiles: los paquetes de ejecucion se
     * declaran con el NIF de la empresa, porque un id numerico en un YAML versionado no
     * significa nada para quien lo revisa y ademas cambia entre entornos.
     */
    Optional<BusinessEntity> findByIdentificationNumber(String identificationNumber);
}
