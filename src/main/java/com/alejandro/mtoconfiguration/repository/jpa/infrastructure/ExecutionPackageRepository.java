package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.ExecutionPackage;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExecutionPackageRepository extends
        CRUDRepository<ExecutionPackage>, MessagingEntityGraphRepository<ExecutionPackage> {

    /**
     * Busqueda por la clave natural, para el find-or-create del importador.
     *
     * <p>Ignora mayusculas porque el origen no es consistente ({@code HR TRACK 3 HAD} y
     * {@code HR Track 3 BIN} conviven en el mismo workbook) y porque es lo que indexa
     * {@code ux_execution_package_name} (V12). El borrado logico lo filtra la
     * {@code @SQLRestriction} de {@code CRUDEntity}, igual que ese indice parcial.
     */
    Optional<ExecutionPackage> findByNameIgnoreCase(String name);

    /**
     * El identificador de la empresa del paquete, sin tocar la asociacion.
     *
     * <p>{@code ExecutionPackage.company} es {@code LAZY}, y quien compara el paquete con lo que
     * trae el maestro —{@code InfrastructureUpsertService}— NO abre transaccion: la entidad le
     * llega detached y un {@code getCompany()} alli revienta con {@code LazyInitializationException}.
     * En JPQL, {@code e.company.id} se resuelve contra la columna {@code COMPANY_ID} y no hace
     * ninguna union.
     */
    @Query("select e.company.id from ExecutionPackage e where e.id = :id")
    Optional<Long> findCompanyIdById(@Param("id") Long id);

    /**
     * Mismo motivo que en StationRepository: ExecutionPackageMasterDataPayloadMapper
     * lee dos colecciones (tracks y stations) y en un unico {@code @EntityGraph}
     * Hibernate las une en la misma sentencia, multiplicando las filas entre si. Un
     * paquete con 200 vias y 30 estaciones son 6.000 filas para publicar un evento.
     * <p>
     * Una consulta por coleccion: ambas devuelven la MISMA instancia gestionada del
     * contexto de persistencia, asi que la entidad queda igual de completa con filas
     * planas.
     */
    @Override
    default Optional<ExecutionPackage> findByIdForMessaging(Long id) {
        Optional<ExecutionPackage> executionPackage = findByIdWithTracksForMessaging(id);

        if (executionPackage.isPresent()) {
            findByIdWithStationsForMessaging(id);
        }

        return executionPackage;
    }

    /**
     * {@code tracks.stations} entra desde V17. Antes no hacia falta: la estacion de la via era
     * un {@code @ManyToOne} y el mapper solo leia su id, que se saca del proxy sin
     * inicializarlo. Ahora son varias, y recorrer una coleccion perezosa con la entidad ya
     * desatachada revienta al publicar el evento.
     */
    @EntityGraph(attributePaths = {
            "company",
            "tracks",
            "tracks.stations"
    })
    @Query("select e from ExecutionPackage e where e.id = :id")
    Optional<ExecutionPackage> findByIdWithTracksForMessaging(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "stations"
    })
    @Query("select e from ExecutionPackage e where e.id = :id")
    Optional<ExecutionPackage> findByIdWithStationsForMessaging(@Param("id") Long id);
}
