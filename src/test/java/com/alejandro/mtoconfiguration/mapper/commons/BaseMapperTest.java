package com.alejandro.mtoconfiguration.mapper.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utilidades de vinculacion padre-hijo que {@link BaseMapper} pone a disposicion de los mappers
 * generados.
 *
 * <p>MapStruct sabe copiar campos, pero no sabe que en una relacion bidireccional el hijo tiene que
 * apuntar al padre. De eso se encargan estos metodos, y de ellos depende que al guardar un perfil
 * sus mensulas lleven la clave ajena puesta. El caso delicado es la sincronizacion con borrado de
 * huerfanos: decide que hijos <b>desaparecen</b> de la coleccion, y equivocarse ahi borra datos.</p>
 */
class BaseMapperTest {

    private static class TestDTO extends BaseDTO {
        TestDTO(Long id) {
            setId(id);
        }
    }

    private static class Parent extends CRUDEntity {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    private static class Child extends CRUDEntity {
        private Parent parent;

        Child(Long id) {
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

        Parent getParent() {
            return parent;
        }

        void setParent(Parent parent) {
            this.parent = parent;
        }
    }

    /** Solo se necesitan los metodos por defecto, asi que las operaciones de mapeo no se usan. */
    private final BaseMapper<TestDTO, Child> mapper = new BaseMapper<>() {
        @Override
        public TestDTO toDTO(Child entity) {
            return null;
        }

        @Override
        public Child toEntity(TestDTO dto) {
            return null;
        }

        @Override
        public List<TestDTO> toListDTO(List<Child> entities) {
            return List.of();
        }

        @Override
        public List<Child> toListEntity(List<TestDTO> dtos) {
            return List.of();
        }

        @Override
        public void mapToDTOs(List<Child> entities, List<TestDTO> dtos) {
        }

        @Override
        public void updateEntityFromDTO(TestDTO dto, Child entity) {
        }

        @Override
        public void updateDTOFromEntity(Child entity, TestDTO dto) {
        }
    };

    @Nested
    @DisplayName("Vinculacion simple")
    class VinculacionSimple {

        @Test
        @DisplayName("cada hijo de la coleccion queda apuntando al padre")
        void coleccion() {
            Parent parent = new Parent();
            List<Child> children = List.of(new Child(1L), new Child(2L));

            mapper.linkCollection(children, parent, Child::setParent);

            assertThat(children).allSatisfy(child -> assertThat(child.getParent()).isSameAs(parent));
        }

        @Test
        @DisplayName("una coleccion o un padre nulos no rompen la vinculacion")
        void coleccionNula() {
            Parent parent = new Parent();

            mapper.linkCollection((List<Child>) null, parent, Child::setParent);
            mapper.linkCollection(List.of(new Child(1L)), null, Child::setParent);
        }

        @Test
        @DisplayName("un hijo unico queda apuntando al padre")
        void hijoUnico() {
            Parent parent = new Parent();
            Child child = new Child(1L);

            mapper.linkEntity(child, parent, Child::setParent);

            assertThat(child.getParent()).isSameAs(parent);
        }

        @Test
        @DisplayName("un hijo o un padre nulos no rompen la vinculacion")
        void hijoNulo() {
            mapper.linkEntity(null, new Parent(), Child::setParent);
            mapper.linkEntity(new Child(1L), null, Child::setParent);
        }
    }

    @Nested
    @DisplayName("Sincronizacion con borrado de huerfanos")
    class Sincronizacion {

        @Test
        @DisplayName("los hijos existentes que no vienen en el DTO se eliminan de la coleccion")
        void eliminaHuerfanos() {
            Parent parent = new Parent();
            Child conservado = new Child(1L);
            Child huerfano = new Child(2L);
            List<Child> entities = new ArrayList<>(List.of(conservado, huerfano));

            mapper.linkCollection(List.of(new TestDTO(1L)), entities, parent, Child::setParent, true);

            assertThat(entities).containsExactly(conservado);
            assertThat(conservado.getParent()).isSameAs(parent);
        }

        @Test
        @DisplayName("un hijo nuevo, sin id todavia, nunca se considera huerfano")
        void hijoNuevoSobrevive() {
            // Es el caso de añadir una mensula dentro de la modificacion de su perfil: la entidad
            // aun no tiene id, asi que no puede estar en la lista de ids del DTO.
            Parent parent = new Parent();
            Child nuevo = new Child(null);
            List<Child> entities = new ArrayList<>(List.of(nuevo));

            mapper.linkCollection(List.of(), entities, parent, Child::setParent, true);

            assertThat(entities).containsExactly(nuevo);
        }

        @Test
        @DisplayName("sin sincronizacion no se elimina nada, solo se vincula")
        void sinSincronizacion() {
            Parent parent = new Parent();
            Child huerfano = new Child(2L);
            List<Child> entities = new ArrayList<>(List.of(huerfano));

            mapper.linkCollection(List.of(new TestDTO(1L)), entities, parent, Child::setParent, false);

            assertThat(entities).containsExactly(huerfano);
            assertThat(huerfano.getParent()).isSameAs(parent);
        }

        @Test
        @DisplayName("una lista de DTO nula vacia la coleccion de hijos ya persistidos")
        void dtosNulos() {
            // Comportamiento deliberado y peligroso: null se trata como "no viene ninguno", no
            // como "no tocar". Queda escrito porque es la diferencia entre conservar y borrar.
            Parent parent = new Parent();
            List<Child> entities = new ArrayList<>(List.of(new Child(1L), new Child(2L)));

            mapper.linkCollection(null, entities, parent, Child::setParent, true);

            assertThat(entities).isEmpty();
        }

        @Test
        @DisplayName("una coleccion de entidades nula no rompe")
        void entidadesNulas() {
            mapper.linkCollection(List.of(new TestDTO(1L)), null, new Parent(), Child::setParent, true);
        }
    }

    @Nested
    @DisplayName("Fusion por id")
    class Fusion {

        @Test
        @DisplayName("el hijo que vuelve con su id se actualiza, no se duplica")
        void fusionaPorId() {
            Parent parent = new Parent();
            Child existente = new Child(1L);
            List<Child> entities = new ArrayList<>(List.of(existente));
            List<Long> actualizados = new ArrayList<>();

            mapper.mergeCollection(List.of(new TestDTO(1L)), entities, parent,
                    dto -> new Child(null), (dto, child) -> actualizados.add(child.getId()),
                    Child::setParent);

            assertThat(entities).containsExactly(existente);
            assertThat(actualizados).containsExactly(1L);
            assertThat(existente.getParent()).isSameAs(parent);
        }

        @Test
        @DisplayName("una lista vacia borra los hijos que ya estaban")
        void listaVaciaBorra() {
            // Es el contrato de la API: mandar la coleccion es declarar cuales son TODOS los
            // hijos, asi que mandarla vacia es decir que ya no hay ninguno.
            Parent parent = new Parent();
            List<Child> entities = new ArrayList<>(List.of(new Child(1L), new Child(2L)));

            mapper.mergeCollection(List.<TestDTO>of(), entities, parent,
                    dto -> new Child(null), (dto, child) -> { }, Child::setParent);

            assertThat(entities).isEmpty();
        }

        @Test
        @DisplayName("una lista nula no toca la coleccion")
        void listaNulaNoToca() {
            // null NO es lo mismo que vacia: es "de esta coleccion no se ha dicho nada". El
            // importador del maestro actualiza el paquete de ejecucion sin mandar sus vias, y
            // mientras null equivalio a vacia, reimportar borraba las 174 vias y, en cascada,
            // los 11.714 perfiles, para volver a crearlo todo a continuacion.
            Parent parent = new Parent();
            Child uno = new Child(1L);
            Child otro = new Child(2L);
            List<Child> entities = new ArrayList<>(List.of(uno, otro));

            mapper.mergeCollection(null, entities, parent,
                    dto -> new Child(null), (dto, child) -> { }, Child::setParent);

            assertThat(entities).containsExactly(uno, otro);
        }
    }

    @Nested
    @DisplayName("Vinculacion muchos a muchos")
    class MuchosAMuchos {

        @Test
        @DisplayName("el vinculo se establece en los dos sentidos")
        void bidireccional() {
            Parent parent = new Parent();
            Set<Child> inverse = new HashSet<>();
            List<Child> children = List.of(new Child(1L));

            mapper.linkManyToMany(children, parent, Child::setParent, (p, c) -> inverse.add(c));

            assertThat(children.getFirst().getParent()).isSameAs(parent);
            assertThat(inverse).containsExactlyElementsOf(children);
        }

        @Test
        @DisplayName("con sincronizacion, los vinculos viejos se retiran con la funcion de borrado")
        void retiraVinculosViejos() {
            Parent parent = new Parent();
            Child conservado = new Child(1L);
            Child retirado = new Child(2L);
            List<Child> entities = new ArrayList<>(List.of(conservado, retirado));
            List<Child> removed = new ArrayList<>();

            mapper.linkManyToMany(
                    List.of(new TestDTO(1L)),
                    entities,
                    parent,
                    Child::setParent,
                    (p, c) -> {
                    },
                    (p, c) -> removed.add(c),
                    true);

            assertThat(removed).containsExactly(retirado);
        }

        @Test
        @DisplayName("sin sincronizacion no se retira ningun vinculo")
        void sinSincronizacion() {
            Parent parent = new Parent();
            List<Child> entities = new ArrayList<>(List.of(new Child(2L)));
            List<Child> removed = new ArrayList<>();

            mapper.linkManyToMany(
                    List.of(new TestDTO(1L)),
                    entities,
                    parent,
                    Child::setParent,
                    (p, c) -> {
                    },
                    (p, c) -> removed.add(c),
                    false);

            assertThat(removed).isEmpty();
        }
    }
}
