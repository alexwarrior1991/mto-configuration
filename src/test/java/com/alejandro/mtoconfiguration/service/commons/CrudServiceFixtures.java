package com.alejandro.mtoconfiguration.service.commons;

import com.alejandro.mtoconfiguration.business.commons.CRUDBusiness;
import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.mapper.commons.BaseMapper;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.repository.jpa.commons.CriteriaSearchRepository;
import com.alejandro.mtoconfiguration.validator.commons.CRUDValidator;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Map;
import java.util.function.Function;

/**
 * Dobles de dominio para ejercitar {@link BaseService} y {@link CRUDService} sin arrancar Spring
 * ni tocar la base de datos.
 *
 * <p>Se prueba la clase base con una entidad inventada, no con {@code Profile} o {@code Track}, a
 * proposito: lo que hay que fijar aqui es el <b>contrato comun</b> —orden de validador, business,
 * repositorio y eventos— y no las reglas de ninguna entidad concreta. Con una entidad real el test
 * fallaria cada vez que cambiase una regla de negocio ajena a lo que se esta comprobando.</p>
 */
final class CrudServiceFixtures {

    private CrudServiceFixtures() {
    }

    /** DTO minimo: solo necesita id (lo usan {@code Utils.exists} y el flujo de update). */
    static class TestDTO extends BaseDTO {

        TestDTO() {
        }

        TestDTO(Long id) {
            setId(id);
        }
    }

    /**
     * Entidad minima. {@code BaseEntity} declara {@code protected Long id} pero deja
     * {@code getId()/setId()} sin implementar, asi que toda entidad concreta —tambien esta— tiene
     * que resolverlos.
     */
    static class TestEntity extends CRUDEntity {

        TestEntity() {
        }

        TestEntity(Long id) {
            this.id = id;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    /**
     * Servicio de prueba. Hereda de {@link CRUDService} para cubrir de una vez el CRUD de
     * {@link BaseService} y el borrado logico que añade su hija.
     *
     * <p>Las colaboraciones son campos mutables porque varias ramas del codigo bajo prueba dependen
     * de que el colaborador sea <b>nulo</b>: {@code getValidator()}, {@code getBusiness()} y
     * {@code getCriteriaSearchRepository()} se consultan con {@code Optional.ofNullable}.</p>
     */
    static class TestService extends CRUDService<TestDTO, TestEntity> {

        private BaseMapper<TestDTO, TestEntity> mapper;
        private CRUDValidator<TestDTO> validator;
        private JpaRepository<TestEntity, Long> repository;
        private CriteriaSearchRepository<TestEntity> criteriaSearchRepository;
        private CRUDBusiness<TestDTO, TestEntity> business;
        private Map<String, Object> searchParams = Map.of();
        private boolean cacheable;

        TestService(BaseMapper<TestDTO, TestEntity> mapper,
                    CRUDValidator<TestDTO> validator,
                    JpaRepository<TestEntity, Long> repository,
                    CriteriaSearchRepository<TestEntity> criteriaSearchRepository,
                    CRUDBusiness<TestDTO, TestEntity> business) {
            this.mapper = mapper;
            this.validator = validator;
            this.repository = repository;
            this.criteriaSearchRepository = criteriaSearchRepository;
            this.business = business;
        }

        @Override
        protected BaseMapper<TestDTO, TestEntity> getMapper() {
            return mapper;
        }

        @Override
        protected CRUDValidator<TestDTO> getValidator() {
            return validator;
        }

        @Override
        public TestEntity getEntity() {
            return new TestEntity();
        }

        @Override
        public TestDTO getDTO() {
            return new TestDTO();
        }

        @Override
        protected JpaRepository<TestEntity, Long> getRepository() {
            return repository;
        }

        @Override
        protected CriteriaSearchRepository<TestEntity> getCriteriaSearchRepository() {
            return criteriaSearchRepository;
        }

        @Override
        protected CRUDBusiness<TestDTO, TestEntity> getBusiness() {
            return business;
        }

        @Override
        protected Map<String, Object> searchParams() {
            return searchParams;
        }

        @Override
        public boolean isCacheable() {
            return cacheable;
        }

        void setValidator(CRUDValidator<TestDTO> validator) {
            this.validator = validator;
        }

        void setBusiness(CRUDBusiness<TestDTO, TestEntity> business) {
            this.business = business;
        }

        void setCriteriaSearchRepository(CriteriaSearchRepository<TestEntity> criteriaSearchRepository) {
            this.criteriaSearchRepository = criteriaSearchRepository;
        }

        void setSearchParams(Map<String, Object> searchParams) {
            this.searchParams = searchParams;
        }

        void setCacheable(boolean cacheable) {
            this.cacheable = cacheable;
        }

        /** {@code applyCondition} es protegido y no se invoca desde ningun metodo publico de la base. */
        <V> void callApplyCondition(BooleanBuilder builder, V value, Function<V, BooleanExpression> function) {
            applyCondition(builder, value, function);
        }
    }
}
