package com.alejandro.mtoconfiguration.repository.jpa.infrastructure;

import com.alejandro.mtoconfiguration.entity.infrastructure.Track;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CRUDRepository;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackRepository extends CRUDRepository<Track>,
        MessagingEntityGraphRepository<Track> {

    /**
     * Busqueda por la clave natural, para el find-or-create del importador.
     *
     * <p>Ignora mayusculas porque el origen no es consistente ({@code HR TRACK 3 HAD} y
     * {@code HR Track 3 BIN} conviven en el mismo workbook) y porque es lo que indexa
     * {@code ux_track_ep_name} (V12). El borrado logico lo filtra la
     * {@code @SQLRestriction} de {@code CRUDEntity}, igual que ese indice parcial.
     */
    Optional<Track> findByExecutionPackageIdAndNameIgnoreCase(Long executionPackageId, String name);

    List<Track> findByExecutionPackageId(Long executionPackageId);

    /**
     * {@code profiles.disconnector} no lo pide TrackMasterDataPayloadMapper, lo impone
     * Hibernate: Profile.disconnector es el lado INVERSO de un {@code @OneToOne} y no
     * puede ser perezoso sin bytecode enhancement, asi que si no se trae en el grafo
     * se carga con un select secundario POR CADA perfil de la via. Una via con 200
     * perfiles hacia 201 consultas para publicar un evento; con esta ruta, una.
     * <p>
     * Es un join a-uno colgando de profiles, no una segunda coleccion: no multiplica
     * filas.
     * <p>
     * {@code stations} SI entra desde V17, y multiplica las filas del resultado —una via de
     * 200 perfiles en 3 estaciones devuelve 600— porque no hay alternativa: hasta V17 la
     * estacion era un {@code @ManyToOne} del que el mapper solo leia el id, y el id de un
     * proxy se lee sin inicializarlo. Ahora es una coleccion, y una coleccion perezosa sobre
     * la entidad ya desatachada revienta con LazyInitializationException al publicar el
     * evento. 600 filas en una consulta es un precio pequeño al lado de eso.
     */
    @Override
    @EntityGraph(attributePaths = {
            "executionPackage",
            "stations",
            "profiles",
            "profiles.disconnector"
    })
    @Query("select t from Track t where t.id = :id")
    Optional<Track> findByIdForMessaging(@Param("id") Long id);
}
