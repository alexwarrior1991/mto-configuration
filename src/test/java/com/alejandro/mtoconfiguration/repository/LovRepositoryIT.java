package com.alejandro.mtoconfiguration.repository;

import com.alejandro.mtoconfiguration.entity.lov.Anchorage;
import com.alejandro.mtoconfiguration.entity.lov.AnchorageFoundation;
import com.alejandro.mtoconfiguration.entity.lov.PoleType;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageFoundationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.AnchorageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.lov.PoleTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consultas de las listas de valores contra la base de datos.
 *
 * <p>{@code findByCode} es la consulta mas usada del proyecto sin darse cuenta: por ella pasan la
 * resolucion de cada LOV que llega dentro de un DTO ({@code LovRelationResolver},
 * {@code LovMapper.findOrMap}), el resolver de codigo a id que cachea {@code LovReferenceResolver}
 * y los dieciseis servicios de catalogo. Aun asi no la ejercitaba ningun test contra PostgreSQL.
 *
 * <p>Se prueba con tres LOV a proposito —{@code Anchorage}, {@code AnchorageFoundation} y
 * {@code PoleType}— porque las dos primeras comparten prefijo. Cada LOV tiene su propia tabla, de
 * modo que un codigo repetido entre catalogos distintos es legitimo y no puede confundirlas.
 */
class LovRepositoryIT extends AbstractCriteriaSearchIT {

    @Autowired
    private AnchorageRepository anchorageRepository;

    @Autowired
    private AnchorageFoundationRepository anchorageFoundationRepository;

    @Autowired
    private PoleTypeRepository poleTypeRepository;

    @BeforeEach
    void seed() {
        anchorage("ANC", "Anclaje simple", true);
        anchorage("ANC2", "Anclaje doble", false);
        anchorageFoundation("ANC", "Cimentacion de anclaje", true);
        poleType("PT1", "Poste tipo 1", true);

        flushAndClear();
    }

    private void anchorage(String code, String description, boolean enabled) {
        Anchorage entity = new Anchorage();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(enabled);
        em.persist(entity);
    }

    private void anchorageFoundation(String code, String description, boolean enabled) {
        AnchorageFoundation entity = new AnchorageFoundation();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(enabled);
        em.persist(entity);
    }

    private void poleType(String code, String description, boolean enabled) {
        PoleType entity = new PoleType();
        entity.setCode(code);
        entity.setDescription(description);
        entity.setEnabled(enabled);
        em.persist(entity);
    }

    @Test
    @DisplayName("findByCode devuelve la fila de su catalogo")
    void findByCode() {
        Anchorage encontrado = anchorageRepository.findByCode("ANC");

        assertThat(encontrado).isNotNull();
        assertThat(encontrado.getDescription()).isEqualTo("Anclaje simple");
    }

    @Test
    @DisplayName("findByCode devuelve null si no existe, no lanza")
    void findByCodeInexistente() {
        // AbstractLovCrudService cuenta con este null para lanzar su propio EntityNotFoundException
        // con el nombre del catalogo: si el repositorio lanzara, el mensaje seria otro.
        assertThat(anchorageRepository.findByCode("NO-EXISTE")).isNull();
    }

    @Test
    @DisplayName("findByCode distingue mayusculas: el codigo es exacto")
    void findByCodeEsSensibleAMayusculas() {
        assertThat(anchorageRepository.findByCode("anc")).isNull();
    }

    @Test
    @DisplayName("un mismo codigo en dos catalogos distintos no se confunde")
    void mismoCodigoEnCatalogosDistintos() {
        // ANC existe en anchorage y en anchorage_foundation: cada repositorio va a su tabla.
        assertThat(anchorageRepository.findByCode("ANC").getDescription())
                .isEqualTo("Anclaje simple");
        assertThat(anchorageFoundationRepository.findByCode("ANC").getDescription())
                .isEqualTo("Cimentacion de anclaje");
    }

    @Test
    @DisplayName("findByCode encuentra tambien un LOV deshabilitado")
    void encuentraLosDeshabilitados() {
        // Las LOV no tienen borrado logico: 'enabled' es un dato, no un filtro de la consulta.
        // Importa porque una entidad antigua puede seguir apuntando a un codigo ya retirado.
        Anchorage deshabilitado = anchorageRepository.findByCode("ANC2");

        assertThat(deshabilitado).isNotNull();
        assertThat(deshabilitado.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("findAll devuelve el catalogo entero de esa LOV")
    void findAll() {
        assertThat(anchorageRepository.findAll())
                .extracting(a -> a.getCode())
                .containsExactlyInAnyOrder("ANC", "ANC2");
        assertThat(poleTypeRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("la busqueda parcial por codigo ignora mayusculas y pagina")
    void findByCodeContainsIgnoreCase() {
        assertThat(anchorageRepository.findByCodeContainsIgnoreCase("anc", PageRequest.of(0, 10)))
                .extracting(a -> a.getCode())
                .containsExactlyInAnyOrder("ANC", "ANC2");

        assertThat(anchorageRepository.findByCodeContainsIgnoreCase("zzz", PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("la busqueda por codigo o descripcion casa por cualquiera de los dos")
    void findByCodeOrDescription() {
        assertThat(anchorageRepository
                .findByCodeLikeIgnoreCaseOrDescriptionLikeIgnoreCase("%ANC2%", "%no-casa%"))
                .extracting(a -> a.getCode())
                .containsExactly("ANC2");

        assertThat(anchorageRepository
                .findByCodeLikeIgnoreCaseOrDescriptionLikeIgnoreCase("%no-casa%", "%doble%"))
                .extracting(a -> a.getCode())
                .containsExactly("ANC2");
    }

    @Test
    @DisplayName("borrar una LOV la borra de verdad: no hay borrado logico")
    void elBorradoDeUnaLovEsFisico() {
        // Lov extiende BaseEntity, no CRUDEntity, asi que no tiene columna 'deleted' ni
        // @SQLRestriction. Es la asimetria con el resto del modelo y conviene tenerla escrita:
        // AbstractLovCrudService.delete() borra la fila, y si alguna entidad la referenciaba la
        // clave ajena lo impedira.
        Anchorage aBorrar = anchorageRepository.findByCode("ANC2");
        anchorageRepository.delete(aBorrar);
        flushAndClear();

        assertThat(anchorageRepository.findByCode("ANC2")).isNull();
        assertThat(((Number) em.createNativeQuery("select count(*) from anchorage").getSingleResult())
                .longValue())
                .as("la fila ya no esta en la tabla")
                .isEqualTo(1);
    }
}
