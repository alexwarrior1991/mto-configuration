package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.configuration.cache.CacheEvictionEvent;
import com.alejandro.mtoconfiguration.configuration.cache.RedisCacheKeyGenerator;
import com.alejandro.mtoconfiguration.core.exception.EntityInUseException;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.model.commons.Alert;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestDTO;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestEntity;
import com.alejandro.mtoconfiguration.service.commons.CrudServiceFixtures.TestService;
import com.alejandro.mtoconfiguration.service.commons.event.BeforeDeleteCheck;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeTracker;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Borrado logico de {@link CRUDService}.
 *
 * <p>El borrado de este proyecto no es un {@code delete} del repositorio: marca la entidad y la
 * vuelve a guardar, de modo que lo unico que impide que una fila borrada siga apareciendo es que
 * {@code entity.delete()} se haya llamado antes del {@code saveAndFlush}. Eso es lo que se fija
 * aqui, junto con el hecho de que un borrado publique evento de baja y no de modificacion, que lo
 * que lo impide ({@code BeforeDeleteCheck}) se mire antes de tocar nada y que los hijos que se van
 * con la entidad publiquen su baja. Contra PostgreSQL, {@code DeleteWithDependentsIT}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CRUDServiceTest {

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

    private TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(mapper, validator, repository, criteriaSearchRepository, business);
        ReflectionTestUtils.setField(service, "applicationEventPublisher", publisher);
        ReflectionTestUtils.setField(service, "pageCacheService", null);
        ReflectionTestUtils.setField(service, "cacheKeyGenerator", null);
    }

    @Test
    @DisplayName("el borrado marca la entidad como borrada y la vuelve a guardar")
    void borradoLogico() {
        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        service.delete(dto);

        ArgumentCaptor<TestEntity> captor = ArgumentCaptor.forClass(TestEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("el borrado valida, delega en el business y marca la entidad, en ese orden")
    void ordenDelBorrado() {
        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        service.delete(dto);

        InOrder order = inOrder(validator, business, repository);
        order.verify(validator).validateBeforeDelete(dto);
        order.verify(business).preDeleteDTOToEntity(dto, entity);
        order.verify(business).deleteEntity(entity);
        order.verify(repository).saveAndFlush(entity);
    }

    @Test
    @DisplayName("el borrado publica evento de baja y de invalidacion de cache")
    void eventosDelBorrado() {
        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        service.delete(dto);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .containsExactly(
                        new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.DELETED),
                        new CacheEvictionEvent("TestService"));
    }

    @Test
    @DisplayName("una alerta DANGER de borrado impide guardar")
    void borradoConErrores() {
        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(validator.validateBeforeDelete(dto)).thenReturn(List.of(Alert.ofDanger("tiene hijos")));

        assertThatThrownBy(() -> service.delete(dto)).isInstanceOf(ValidationException.class);

        verify(repository, never()).saveAndFlush(any());
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("borrar un id inexistente es un NotFoundException (404), con la entidad y el id")
    void borradoIdInexistente() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new TestDTO(9L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("TestEntity Object not found with id 9");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("borrar un DTO nulo o sin id no hace nada, en silencio")
    void borradoNulo() {
        // Es el comportamiento actual y conviene dejarlo escrito: softDelete usa ifPresent, asi que
        // un DTO sin id no borra nada Y NO avisa. Si alguna vez se decide que debe fallar, este
        // test es el que hay que cambiar a proposito.
        service.delete(null);
        service.delete(new TestDTO());

        verifyNoInteractions(repository);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("sin validador ni business el borrado sigue marcando la entidad")
    void borradoSinColaboradoresOpcionales() {
        service.setValidator(null);
        service.setBusiness(null);

        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        service.delete(dto);

        assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("lo que impide el borrado se mira despues del validador y antes de tocar nada: si lanza, ni se marca, ni se guarda, ni se publica")
    void loQueImpideElBorrado() {
        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);
        BeforeDeleteCheck check = mock(BeforeDeleteCheck.class);
        ReflectionTestUtils.setField(service, "beforeDeleteChecks", List.of(check));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        doThrow(new EntityInUseException("tiene 2 perfiles", List.of(new EntityInUseException.Dependent("profiles", 2))))
                .when(check).beforeDelete(entity);

        assertThatThrownBy(() -> service.delete(dto)).isInstanceOf(EntityInUseException.class);

        InOrder order = inOrder(validator, check);
        order.verify(validator).validateBeforeDelete(dto);
        order.verify(check).beforeDelete(entity);
        verifyNoInteractions(business);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(publisher);
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("lo que el borrado escribe ademas de la raiz (sus hijos propios) publica su DELETED, despues del de la raiz")
    void elBorradoPublicaLosHijosPropios() {
        TestDTO dto = new TestDTO(1L);
        TestEntity entity = new TestEntity(1L);
        TestEntity hijo = new TestEntity(2L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        // El listener JPA apunta cada fila que el flush escribe; aqui lo hace el repositorio simulado.
        when(repository.saveAndFlush(entity)).thenAnswer(invocation -> {
            EntityChangeTracker.record(hijo, EntityChangeOperation.DELETED);
            EntityChangeTracker.record(entity, EntityChangeOperation.DELETED);
            return entity;
        });

        service.delete(dto);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.times(3)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .containsExactly(
                        new EntityChangeApplicationEvent<>(entity, EntityChangeOperation.DELETED),
                        new EntityChangeApplicationEvent<>(hijo, EntityChangeOperation.DELETED),
                        new CacheEvictionEvent("TestService"));
        // Y el ambito se cierra con el borrado.
        assertThat(EntityChangeTracker.written()).isEmpty();
    }
}
