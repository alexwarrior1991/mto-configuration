package com.alejandro.mtoconfiguration.service.lov.commons;

import com.alejandro.mtoconfiguration.configuration.cache.LovCacheEvictionEvent;
import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import com.alejandro.mtoconfiguration.mapper.lov.commons.LovMapper;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.repository.jpa.lov.commons.LovRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contrato de {@link AbstractLovCrudService}, base de los dieciseis servicios de listas de valores.
 *
 * <p>Ninguno de esos dieciseis redefine el CRUD: solo enchufan repositorio, mapper y algun hook. Por
 * eso el sitio donde tiene sentido fijar el comportamiento —mensajes de error, hooks y, sobre todo,
 * que una operacion en lote publique <b>un unico</b> evento de invalidacion— es esta clase.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbstractLovCrudServiceTest {

    /** Entidad LOV concreta minima: {@code BaseEntity} deja {@code getId()/setId()} sin resolver. */
    static class TestLov extends Lov {

        private Long entityId;

        TestLov() {
        }

        TestLov(Long id) {
            this.entityId = id;
        }

        @Override
        public Long getId() {
            return entityId;
        }

        @Override
        public void setId(Long id) {
            this.entityId = id;
        }
    }

    /** Servicio concreto que ademas anota que hooks se han invocado y en que orden. */
    static class TestLovService extends AbstractLovCrudService<LovDTO, TestLov> {

        private final List<String> hooks = new ArrayList<>();

        TestLovService(LovRepository<TestLov> repository,
                       LovMapper<LovDTO, TestLov> mapper,
                       ApplicationEventPublisher publisher) {
            super(repository, mapper, publisher);
        }

        @Override
        protected String getEntityName() {
            return "TestLov";
        }

        @Override
        protected void beforeCreate(LovDTO dto, TestLov entity) {
            hooks.add("beforeCreate");
        }

        @Override
        protected void afterCreate(TestLov entity) {
            hooks.add("afterCreate");
        }

        @Override
        protected void beforeUpdate(LovDTO dto, TestLov entity) {
            hooks.add("beforeUpdate");
        }

        @Override
        protected void afterUpdate(TestLov entity) {
            hooks.add("afterUpdate");
        }

        @Override
        protected void beforeDelete(TestLov entity) {
            hooks.add("beforeDelete");
        }

        List<String> hooks() {
            return hooks;
        }
    }

    @Mock
    private LovRepository<TestLov> repository;
    @Mock
    private LovMapper<LovDTO, TestLov> mapper;
    @Mock
    private ApplicationEventPublisher publisher;

    private TestLovService service;

    @BeforeEach
    void setUp() {
        service = new TestLovService(repository, mapper, publisher);
    }

    private static LovDTO dto(Long id, String code) {
        LovDTO dto = new LovDTO();
        dto.setId(id);
        dto.setCode(code);
        return dto;
    }

    @Nested
    @DisplayName("Lectura")
    class Lectura {

        @Test
        @DisplayName("findById(null) devuelve null sin consultar")
        void findByIdNulo() {
            assertThat(service.findById(null)).isNull();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("findById de un id inexistente nombra la LOV y el id")
        void findByIdInexistente() {
            when(repository.findById(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(3L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("TestLov not found with id 3");
        }

        @Test
        @DisplayName("findById devuelve el DTO mapeado")
        void findByIdOk() {
            TestLov entity = new TestLov(3L);
            LovDTO expected = dto(3L, "A");
            when(repository.findById(3L)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(expected);

            assertThat(service.findById(3L)).isSameAs(expected);
        }

        @Test
        @DisplayName("findByCode con codigo vacio o en blanco devuelve null sin consultar")
        void findByCodeEnBlanco() {
            assertThat(service.findByCode(null)).isNull();
            assertThat(service.findByCode("   ")).isNull();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("findByCode de un codigo inexistente nombra la LOV y el codigo")
        void findByCodeInexistente() {
            when(repository.findByCode("XX")).thenReturn(null);

            assertThatThrownBy(() -> service.findByCode("XX"))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("TestLov not found with code XX");
        }

        @Test
        @DisplayName("findAll mapea la lista completa")
        void findAll() {
            List<TestLov> entities = List.of(new TestLov(1L));
            List<LovDTO> dtos = List.of(dto(1L, "A"));
            when(repository.findAll()).thenReturn(entities);
            when(mapper.toListDTO(entities)).thenReturn(dtos);

            assertThat(service.findAll()).isEqualTo(dtos);
        }
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("el alta mapea, aplica los hooks alrededor del guardado y publica invalidacion")
        void altaOk() {
            LovDTO dto = dto(null, "A");
            TestLov entity = new TestLov();
            TestLov saved = new TestLov(1L);

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(saved);
            when(mapper.toDTO(saved)).thenReturn(dto);

            assertThat(service.create(dto)).isSameAs(dto);

            assertThat(service.hooks()).containsExactly("beforeCreate", "afterCreate");
            verify(publisher).publishEvent(new LovCacheEvictionEvent("TestLov"));
        }

        @Test
        @DisplayName("un DTO nulo o sin codigo no llega al repositorio")
        void altaInvalida() {
            assertThatThrownBy(() -> service.create(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TestLov data is required");

            assertThatThrownBy(() -> service.create(dto(null, "  ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TestLov code is required");

            verifyNoInteractions(repository);
            verifyNoInteractions(publisher);
        }
    }

    @Nested
    @DisplayName("Modificacion")
    class Modificacion {

        @Test
        @DisplayName("la modificacion carga, vuelca el DTO sobre la entidad y publica invalidacion")
        void updateOk() {
            LovDTO dto = dto(1L, "A");
            TestLov entity = new TestLov(1L);

            when(repository.findById(1L)).thenReturn(Optional.of(entity));
            when(repository.save(entity)).thenReturn(entity);
            when(mapper.toDTO(entity)).thenReturn(dto);

            assertThat(service.update(1L, dto)).isSameAs(dto);

            InOrder order = inOrder(repository, mapper);
            order.verify(repository).findById(1L);
            order.verify(mapper).updateEntityFromDTO(dto, entity);
            order.verify(repository).save(entity);

            assertThat(service.hooks()).containsExactly("beforeUpdate", "afterUpdate");
            verify(publisher).publishEvent(new LovCacheEvictionEvent("TestLov"));
        }

        @Test
        @DisplayName("modificar sin id es un error de argumento, no una consulta")
        void updateSinId() {
            assertThatThrownBy(() -> service.update(null, dto(null, "A")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Id is required");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("modificar un id inexistente nombra la LOV y el id")
        void updateInexistente() {
            when(repository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(9L, dto(9L, "A")))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("TestLov not found with id 9");
        }
    }

    @Nested
    @DisplayName("Borrado")
    class Borrado {

        @Test
        @DisplayName("el borrado aplica el hook previo, borra y publica invalidacion")
        void borradoOk() {
            TestLov entity = new TestLov(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(entity));

            service.delete(1L);

            assertThat(service.hooks()).containsExactly("beforeDelete");
            verify(repository).delete(entity);
            verify(publisher).publishEvent(new LovCacheEvictionEvent("TestLov"));
        }

        @Test
        @DisplayName("borrar sin id es un error de argumento")
        void borradoSinId() {
            assertThatThrownBy(() -> service.delete(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Id is required");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("borrar un id inexistente nombra la LOV y el id")
        void borradoInexistente() {
            when(repository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(9L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("TestLov not found with id 9");

            verify(repository, never()).delete(any(TestLov.class));
        }
    }

    @Nested
    @DisplayName("Operaciones en lote")
    class Lote {

        @Test
        @DisplayName("el alta en lote publica UNA sola invalidacion, no una por elemento")
        void bulkCreateUnSoloEvento() {
            // Decision explicita del codigo: un evento por entidad dispararia cientos de SCAN
            // sobre el keyspace de Redis para invalidar exactamente lo mismo.
            List<LovDTO> dtos = List.of(dto(null, "A"), dto(null, "B"), dto(null, "C"));
            List<TestLov> saved = List.of(new TestLov(1L), new TestLov(2L), new TestLov(3L));

            when(mapper.toEntity(any(LovDTO.class))).thenReturn(new TestLov());
            when(repository.saveAll(anyList())).thenReturn(saved);
            when(mapper.toListDTO(saved)).thenReturn(dtos);

            assertThat(service.bulkCreate(dtos)).isEqualTo(dtos);

            verify(publisher, times(1)).publishEvent(new LovCacheEvictionEvent("TestLov"));
            assertThat(service.hooks())
                    .containsExactly("beforeCreate", "beforeCreate", "beforeCreate",
                            "afterCreate", "afterCreate", "afterCreate");
        }

        @Test
        @DisplayName("la modificacion en lote publica UNA sola invalidacion")
        void bulkUpdateUnSoloEvento() {
            List<LovDTO> dtos = List.of(dto(1L, "A"), dto(2L, "B"));
            TestLov first = new TestLov(1L);
            TestLov second = new TestLov(2L);
            List<TestLov> saved = List.of(first, second);

            when(repository.findById(1L)).thenReturn(Optional.of(first));
            when(repository.findById(2L)).thenReturn(Optional.of(second));
            when(repository.saveAll(anyList())).thenReturn(saved);
            when(mapper.toListDTO(saved)).thenReturn(dtos);

            assertThat(service.bulkUpdate(dtos)).isEqualTo(dtos);

            verify(publisher, times(1)).publishEvent(new LovCacheEvictionEvent("TestLov"));
        }

        @Test
        @DisplayName("un lote nulo o vacio es un error de argumento, no un no-op")
        void loteVacio() {
            assertThatThrownBy(() -> service.bulkCreate(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TestLov list is required");
            assertThatThrownBy(() -> service.bulkCreate(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TestLov list is required");
            assertThatThrownBy(() -> service.bulkUpdate(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("TestLov list is required");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("un elemento sin id aborta la modificacion en lote antes de guardar nada")
        void bulkUpdateSinId() {
            when(repository.findById(1L)).thenReturn(Optional.of(new TestLov(1L)));

            assertThatThrownBy(() -> service.bulkUpdate(List.of(dto(1L, "A"), dto(null, "B"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Id is required");

            verify(repository, never()).saveAll(anyList());
            verifyNoInteractions(publisher);
        }

        @Test
        @DisplayName("un elemento inexistente aborta la modificacion en lote")
        void bulkUpdateInexistente() {
            when(repository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.bulkUpdate(List.of(dto(1L, "A"))))
                    .isInstanceOf(EntityNotFoundException.class);

            verify(repository, never()).saveAll(anyList());
        }
    }
}
