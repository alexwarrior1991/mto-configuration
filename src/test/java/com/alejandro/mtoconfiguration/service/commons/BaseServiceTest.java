package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import com.alejandro.mtoconfiguration.core.exception.BaseException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.model.commons.CachedPageDTO;
import com.alejandro.mtoconfiguration.model.commons.SearchRequestDTO;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestDTO;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestEntity;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestService;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contrato de {@link BaseService}, la clase de la que cuelga todo el CRUD del proyecto.
 *
 * <p>Lo que se fija aqui no es "que guarde": es el <b>orden</b> en que colaboran validador, mapper,
 * business y repositorio, y que cada operacion publique los eventos que le tocan. Ese orden es
 * justamente lo que un servicio hijo no puede ver cuando redefine un hook, y lo que se rompe en
 * silencio al reordenar una linea del encadenado de {@code Optional}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseServiceTest {

    @Mock
    private BaseMapper<TestDTO, TestEntity> mapper;
    @Mock
    private CRUDValidator<TestDTO> validator;
    @Mock
    private JpaRepository<TestEntity, Long> repository;
    @Mock
    private CriteriaSearchRepository<TestEntity> criteriaSearchRepository;
    @Mock
    private CRUDBusiness<TestDTO, TestEntity> business;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private PageCacheService pageCacheService;
    @Mock
    private RedisCacheKeyGenerator cacheKeyGenerator;
    @Mock
    private EntityManager entityManager;

    private TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(mapper, validator, repository, criteriaSearchRepository, business);

        // BaseService recibe estas colaboraciones por inyeccion de campo, no por constructor.
        ReflectionTestUtils.setField(service, "applicationEventPublisher", publisher);
        ReflectionTestUtils.setField(service, "pageCacheService", pageCacheService);
        ReflectionTestUtils.setField(service, "cacheKeyGenerator", cacheKeyGenerator);
        ReflectionTestUtils.setField(service, "em", entityManager);
    }

    private List<Object> publishedEvents() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, times(publishedEventCount())).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    private int publishedEventCount() {
        return org.mockito.Mockito.mockingDetails(publisher).getInvocations().size();
    }

    @Nested
    @DisplayName("Alta")
    class Alta {

        @Test
        @DisplayName("el alta encadena validador, business, repositorio y mapper en ese orden")
        void createOrden() {
            TestDTO dto = new TestDTO();
            TestEntity entity = new TestEntity();
            TestEntity saved = new TestEntity(1L);

            when(validator.validateBeforeSave(dto)).thenReturn(List.of());
            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.saveAndFlush(entity)).thenReturn(saved);

            assertThat(service.create(dto)).isSameAs(dto);

            InOrder order = inOrder(validator, mapper, business, repository);
            order.verify(validator).validateBeforeSave(dto);
            order.verify(mapper).toEntity(dto);
            order.verify(business).preMapperDTOToEntity(dto, entity);
            order.verify(business).postValidationDTOToEntity(dto, entity);
            order.verify(repository).saveAndFlush(entity);
            order.verify(mapper).updateDTOFromEntity(saved, dto);
        }

        @Test
        @DisplayName("el alta publica el evento de creacion y el de invalidacion de cache")
        void createEventos() {
            TestDTO dto = new TestDTO();
            TestEntity entity = new TestEntity();
            TestEntity saved = new TestEntity(7L);

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.saveAndFlush(entity)).thenReturn(saved);

            service.create(dto);

            assertThat(publishedEvents())
                    .containsExactly(
                            new EntityChangeApplicationEvent<>(saved, EntityChangeOperation.CREATED),
                            new CacheEvictionEvent("TestService"));
        }

        @Test
        @DisplayName("una alerta de nivel DANGER aborta el alta sin tocar el repositorio")
        void createConErrores() {
            TestDTO dto = new TestDTO();
            when(validator.validateBeforeSave(dto)).thenReturn(List.of(Alert.ofDanger("error", "campo")));

            assertThatThrownBy(() -> service.create(dto)).isInstanceOf(ValidationException.class);

            verifyNoInteractions(repository);
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("una alerta que no es DANGER no impide el alta")
        void createConAvisos() {
            TestDTO dto = new TestDTO();
            TestEntity entity = new TestEntity();

            when(validator.validateBeforeSave(dto)).thenReturn(List.of(Alert.ofWarning("aviso", "campo")));
            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.saveAndFlush(entity)).thenReturn(new TestEntity(1L));

            service.create(dto);

            verify(repository).saveAndFlush(entity);
        }

        @Test
        @DisplayName("sin validador ni business el alta sigue funcionando")
        void createSinColaboradoresOpcionales() {
            service.setValidator(null);
            service.setBusiness(null);

            TestDTO dto = new TestDTO();
            TestEntity entity = new TestEntity();
            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.saveAndFlush(entity)).thenReturn(new TestEntity(3L));

            assertThat(service.create(dto)).isSameAs(dto);
        }

        @Test
        @DisplayName("crear un DTO nulo es un error de servicio, no un NullPointerException")
        void createNulo() {
            assertThatThrownBy(() -> service.create(null))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("No se puede crear un objeto nulo");
        }
    }

    @Nested
    @DisplayName("Alta en lote")
    class AltaEnLote {

        @Test
        @DisplayName("el lote publica un evento por entidad y una sola invalidacion de cache")
        void bulkCreateEventos() {
            List<TestDTO> dtos = List.of(new TestDTO(), new TestDTO());
            List<TestEntity> entities = List.of(new TestEntity(), new TestEntity());
            List<TestEntity> saved = List.of(new TestEntity(1L), new TestEntity(2L));

            when(mapper.toListEntity(dtos)).thenReturn(entities);
            when(repository.saveAll(entities)).thenReturn(saved);
            when(mapper.toListDTO(saved)).thenReturn(dtos);

            assertThat(service.bulkCreate(dtos)).isEqualTo(dtos);

            verify(repository).flush();
            assertThat(publishedEvents())
                    .containsExactly(
                            new EntityChangeApplicationEvent<>(saved.get(0), EntityChangeOperation.CREATED),
                            new EntityChangeApplicationEvent<>(saved.get(1), EntityChangeOperation.CREATED),
                            new CacheEvictionEvent("TestService"));
        }

        @Test
        @DisplayName("un lote nulo o vacio devuelve lista vacia sin tocar el repositorio")
        void bulkCreateVacio() {
            assertThat(service.bulkCreate(null)).isEmpty();
            assertThat(service.bulkCreate(List.of())).isEmpty();

            verifyNoInteractions(repository);
            verify(validator, never()).validateBeforeBulkSave(anyList());
        }

        @Test
        @DisplayName("un error de validacion de lote aborta el alta completa")
        void bulkCreateConErrores() {
            List<TestDTO> dtos = List.of(new TestDTO());
            when(validator.validateBeforeBulkSave(dtos)).thenReturn(List.of(Alert.ofDanger("error")));

            assertThatThrownBy(() -> service.bulkCreate(dtos)).isInstanceOf(ValidationException.class);

            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("Modificacion")
    class Modificacion {

        @Test
        @DisplayName("la modificacion carga la entidad, aplica los hooks y publica UPDATED")
        void updateOrden() {
            TestDTO dto = new TestDTO(5L);
            TestEntity entity = new TestEntity(5L);
            TestEntity saved = new TestEntity(5L);

            when(repository.findById(5L)).thenReturn(Optional.of(entity));
            when(repository.saveAndFlush(entity)).thenReturn(saved);

            assertThat(service.update(dto)).isSameAs(dto);

            InOrder order = inOrder(validator, repository, business, mapper);
            order.verify(validator).validateBeforeUpdate(dto);
            order.verify(repository).findById(5L);
            order.verify(business).preMapperDTOToEntity(dto, entity);
            order.verify(mapper).updateEntityFromDTO(dto, entity);
            order.verify(business).postValidationDTOToEntity(dto, entity);
            order.verify(repository).saveAndFlush(entity);
            order.verify(mapper).updateDTOFromEntity(saved, dto);

            assertThat(publishedEvents())
                    .containsExactly(
                            new EntityChangeApplicationEvent<>(saved, EntityChangeOperation.UPDATED),
                            new CacheEvictionEvent("TestService"));
        }

        @Test
        @DisplayName("modificar un DTO sin id no llega al repositorio")
        void updateSinId() {
            assertThatThrownBy(() -> service.update(new TestDTO()))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("No se puede actualizar un objeto nulo o sin ID");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("modificar un id inexistente nombra la entidad y el id en el error")
        void updateIdInexistente() {
            when(repository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(new TestDTO(9L)))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("TestEntity Object not found with id 9");
        }

        @Test
        @DisplayName("modificar un DTO nulo es un error de servicio")
        void updateNulo() {
            assertThatThrownBy(() -> service.update(null)).isInstanceOf(BaseException.class);
        }
    }

    @Nested
    @DisplayName("Modificacion en lote")
    class ModificacionEnLote {

        @Test
        @DisplayName("el lote publica un UPDATED por entidad y una sola invalidacion de cache")
        void bulkUpdateEventos() {
            TestDTO first = new TestDTO(1L);
            TestDTO second = new TestDTO(2L);
            TestEntity firstEntity = new TestEntity(1L);
            TestEntity secondEntity = new TestEntity(2L);
            List<TestDTO> dtos = List.of(first, second);
            List<TestEntity> saved = List.of(firstEntity, secondEntity);

            when(repository.findById(1L)).thenReturn(Optional.of(firstEntity));
            when(repository.findById(2L)).thenReturn(Optional.of(secondEntity));
            when(repository.saveAll(anyList())).thenReturn(saved);
            when(mapper.toListDTO(saved)).thenReturn(dtos);

            assertThat(service.bulkUpdate(dtos)).isEqualTo(dtos);

            verify(repository).flush();
            assertThat(publishedEvents())
                    .containsExactly(
                            new EntityChangeApplicationEvent<>(firstEntity, EntityChangeOperation.UPDATED),
                            new EntityChangeApplicationEvent<>(secondEntity, EntityChangeOperation.UPDATED),
                            new CacheEvictionEvent("TestService"));
        }

        @Test
        @DisplayName("un id inexistente en mitad del lote aborta la operacion entera")
        void bulkUpdateIdInexistente() {
            when(repository.findById(1L)).thenReturn(Optional.of(new TestEntity(1L)));
            when(repository.findById(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.bulkUpdate(List.of(new TestDTO(1L), new TestDTO(2L))))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("TestEntity Object not found with id 2");

            verify(repository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("un elemento sin id aborta el lote")
        void bulkUpdateSinId() {
            assertThatThrownBy(() -> service.bulkUpdate(List.of(new TestDTO())))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("No se puede actualizar un objeto nulo o sin ID");
        }

        @Test
        @DisplayName("un lote nulo o vacio devuelve lista vacia sin tocar el repositorio")
        void bulkUpdateVacio() {
            assertThat(service.bulkUpdate(null)).isEmpty();
            assertThat(service.bulkUpdate(List.of())).isEmpty();

            verifyNoInteractions(repository);
        }
    }

    @Nested
    @DisplayName("Cancelacion")
    class Cancelacion {

        @Test
        @DisplayName("cancelar valida, aplica el hook de cancelacion y publica UPDATED, no DELETED")
        void cancelOrden() {
            TestDTO dto = new TestDTO(4L);
            TestEntity entity = new TestEntity(4L);

            when(repository.getReferenceById(4L)).thenReturn(entity);
            when(repository.saveAndFlush(entity)).thenReturn(entity);

            assertThat(service.cancel(dto)).isSameAs(dto);

            InOrder order = inOrder(validator, business, mapper, repository);
            order.verify(validator).validateBeforeCancel(dto);
            order.verify(business).preCancelDTOToEntity(dto, entity);
            order.verify(mapper).updateEntityFromDTO(dto, entity);
            order.verify(repository).saveAndFlush(entity);

            assertThat(publishedEvents())
                    .containsExactly(
                            new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.UPDATED),
                            new CacheEvictionEvent("TestService"));
        }

        @Test
        @DisplayName("una alerta DANGER de cancelacion impide guardar")
        void cancelConErrores() {
            TestDTO dto = new TestDTO(4L);
            when(repository.getReferenceById(4L)).thenReturn(new TestEntity(4L));
            when(validator.validateBeforeCancel(dto)).thenReturn(List.of(Alert.ofDanger("no se puede")));

            assertThatThrownBy(() -> service.cancel(dto)).isInstanceOf(ValidationException.class);

            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("cancelar un DTO nulo o sin id es un error de servicio")
        void cancelNulo() {
            assertThatThrownBy(() -> service.cancel(null))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("No se puede cancelar un objeto nulo o sin ID");
            assertThatThrownBy(() -> service.cancel(new TestDTO()))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("No se puede cancelar un objeto nulo o sin ID");
        }
    }

    @Nested
    @DisplayName("Lectura")
    class Lectura {

        @Test
        @DisplayName("getById aplica los hooks de entidad a DTO en orden")
        void getByIdOrden() {
            TestEntity entity = new TestEntity(2L);
            when(repository.getReferenceById(2L)).thenReturn(entity);

            TestDTO result = service.getById(2L);

            InOrder order = inOrder(business, mapper);
            order.verify(business).preMapperEntityToDTO(eq(entity), any(TestDTO.class));
            order.verify(mapper).updateDTOFromEntity(eq(entity), any(TestDTO.class));
            order.verify(business).postMapperEntityToDTO(eq(entity), any(TestDTO.class));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("getById(null) es un error de servicio, no una consulta")
        void getByIdNulo() {
            assertThatThrownBy(() -> service.getById(null))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("El ID proporcionado no puede ser nulo");

            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("findAll sin resultados devuelve lista vacia sin pasar por el mapper")
        void findAllVacio() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(service.findAll()).isEmpty();

            verify(mapper, never()).toListDTO(anyList());
        }

        @Test
        @DisplayName("findAll con resultados los mapea a DTO")
        void findAllConResultados() {
            List<TestEntity> entities = List.of(new TestEntity(1L));
            List<TestDTO> dtos = List.of(new TestDTO(1L));

            when(repository.findAll()).thenReturn(entities);
            when(mapper.toListDTO(entities)).thenReturn(dtos);

            assertThat(service.findAll()).isEqualTo(dtos);
        }

        @Test
        @DisplayName("un servicio no cacheable pagina contra el repositorio, sin pasar por la cache")
        void findAllPaginadoNoCacheable() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity(1L)));
            Page<TestDTO> mapped = new PageImpl<>(List.of(new TestDTO(1L)));

            when(repository.findAll(pageable)).thenReturn(page);
            when(mapper.mapToDTOs(page)).thenReturn(mapped);

            assertThat(service.findAll(pageable)).isEqualTo(mapped);

            verifyNoInteractions(pageCacheService);
        }

        @Test
        @DisplayName("un servicio cacheable delega la pagina en PageCacheService con la clave generada")
        void findAllPaginadoCacheable() {
            service.setCacheable(true);
            Pageable pageable = PageRequest.of(0, 20);

            when(cacheKeyGenerator.buildKey(service, "findAll", pageable)).thenReturn("clave");
            when(pageCacheService.getPage(eq("clave"), any()))
                    .thenReturn(CachedPageDTO.from(new PageImpl<>(List.of(new TestDTO(1L)))));

            assertThat(service.findAll(pageable).getContent()).hasSize(1);

            verify(pageCacheService).getPage(eq("clave"), any());
            verify(repository, never()).findAll(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("Busqueda")
    class Busqueda {

        private SearchRequestDTO request;

        @BeforeEach
        void setUpRequest() {
            request = new SearchRequestDTO();
            request.setFilters(Map.of("name", "abc"));
        }

        @Test
        @DisplayName("la validacion de busqueda corre ANTES de la cache, asi que tambien en un acierto")
        void validacionFueraDeLaCache() {
            // Invariante documentado en BaseService: si la validacion viviese dentro del bloque
            // cacheado, un acierto de cache devolveria resultados para una peticion invalida.
            service.setCacheable(true);
            when(validator.validateBeforeSearch(request)).thenReturn(List.of(Alert.ofDanger("filtro invalido")));

            assertThatThrownBy(() -> service.search(request)).isInstanceOf(ValidationException.class);

            verifyNoInteractions(pageCacheService);
            verifyNoInteractions(criteriaSearchRepository);
        }

        @Test
        @DisplayName("sin repositorio de criteria la busqueda falla en voz alta")
        void sinRepositorioDeCriteria() {
            service.setCriteriaSearchRepository(null);

            assertThatThrownBy(() -> service.search(request))
                    .isInstanceOf(BaseException.class)
                    .hasMessage("Search method not implemented (CriteriaSearchRepository is null)");
        }

        @Test
        @DisplayName("un servicio no cacheable consulta siempre el repositorio de criteria")
        void busquedaNoCacheable() {
            Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity(1L)));
            Page<TestDTO> mapped = new PageImpl<>(List.of(new TestDTO(1L)));
            Map<String, Object> params = Map.of("userId", "u1");
            service.setSearchParams(params);

            when(criteriaSearchRepository.criteriaSearchWithChildren(
                    TestEntity.class, request, entityManager, params)).thenReturn(page);
            when(mapper.mapToDTOs(page)).thenReturn(mapped);

            assertThat(service.search(request)).isEqualTo(mapped);

            verifyNoInteractions(pageCacheService);
        }

        @Test
        @DisplayName("en un acierto de cache no se vuelve a consultar el repositorio de criteria")
        void busquedaCacheableAcierto() {
            service.setCacheable(true);

            when(cacheKeyGenerator.buildKey(service, "search", request)).thenReturn("clave");
            when(pageCacheService.getSearch(eq("clave"), any()))
                    .thenReturn(CachedPageDTO.from(new PageImpl<>(List.of(new TestDTO(1L)))));

            assertThat(service.search(request).getContent()).hasSize(1);

            verifyNoInteractions(criteriaSearchRepository);
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("en un fallo de cache el loader si consulta el repositorio de criteria")
        void busquedaCacheableFallo() {
            service.setCacheable(true);
            Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity(1L)));

            when(cacheKeyGenerator.buildKey(service, "search", request)).thenReturn("clave");
            when(mapper.mapToDTOs(page)).thenReturn(new PageImpl<>(List.of(new TestDTO(1L))));
            when(criteriaSearchRepository.criteriaSearchWithChildren(any(), any(), any(), any()))
                    .thenReturn(page);
            when(pageCacheService.getSearch(anyString(), any())).thenAnswer(invocation ->
                    CachedPageDTO.from(((Supplier<Page<TestDTO>>) invocation.getArgument(1)).get()));

            assertThat(service.search(request).getContent()).hasSize(1);

            verify(criteriaSearchRepository).criteriaSearchWithChildren(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Filtros QueryDSL")
    class Filtros {

        @Test
        @DisplayName("applyCondition ignora nulos y cadenas en blanco, y aplica el resto")
        void applyCondition() {
            BooleanBuilder builder = new BooleanBuilder();

            service.callApplyCondition(builder, null, (String value) -> Expressions.booleanTemplate("1 = 1"));
            assertThat(builder.hasValue()).as("un valor nulo no filtra").isFalse();

            service.callApplyCondition(builder, "   ", (String value) -> Expressions.booleanTemplate("1 = 1"));
            assertThat(builder.hasValue()).as("una cadena en blanco no filtra").isFalse();

            service.callApplyCondition(builder, "abc", (String value) -> Expressions.booleanTemplate("1 = 1"));
            assertThat(builder.hasValue()).as("un valor con contenido si filtra").isTrue();
        }

        @Test
        @DisplayName("applyCondition acepta valores no textuales sin tratarlos como blancos")
        void applyConditionNoTexto() {
            BooleanBuilder builder = new BooleanBuilder();

            service.callApplyCondition(builder, 0L, (Long value) -> Expressions.booleanTemplate("1 = 1"));

            assertThat(builder.hasValue()).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalidacion de cache")
    class InvalidacionDeCache {

        @Test
        @DisplayName("el evento de invalidacion lleva el nombre del servicio concreto")
        void nombreDelServicio() {
            TestDTO dto = new TestDTO();
            when(mapper.toEntity(dto)).thenReturn(new TestEntity());
            when(repository.saveAndFlush(any())).thenReturn(new TestEntity(1L));

            service.create(dto);

            assertThat(publishedEvents()).contains(new CacheEvictionEvent("TestService"));
        }

        @Test
        @DisplayName("una entidad sin id no genera evento de cambio, solo el de cache")
        void entidadSinId() {
            // getId() nulo significa que el flush no ha asignado clave: publicar el evento dejaria
            // a los consumidores con un identificador que no pueden resolver.
            TestDTO dto = new TestDTO();
            when(mapper.toEntity(dto)).thenReturn(new TestEntity());
            when(repository.saveAndFlush(any())).thenReturn(new TestEntity());

            service.create(dto);

            assertThat(publishedEvents()).containsExactly(new CacheEvictionEvent("TestService"));
        }
    }
}
