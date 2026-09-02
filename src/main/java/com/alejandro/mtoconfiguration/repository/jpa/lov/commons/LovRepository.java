package com.alejandro.mtoconfiguration.repository.jpa.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;

@NoRepositoryBean
public interface LovRepository<E extends Lov> extends JpaRepository<E, Long>, QuerydslPredicateExecutor<E> {

    E findByCode(String code);
    Page<E> findByCodeContainsIgnoreCase(String code, Pageable pageable );
    List<E> findByCodeLikeIgnoreCaseOrDescriptionLikeIgnoreCase(String code, String description );

    /**
     * Carga de golpe todas las LOV cuyo codigo este en la coleccion.
     *
     * <p>Existe para el importador del catalogo maestro: resolver ~1000 codigos
     * con {@link #findByCode(String)} serian ~1000 SELECT sueltos. Con esto es
     * una consulta por entidad LOV.
     *
     * <p>Devuelve solo las que existen, sin garantizar orden ni completitud
     * respecto a la coleccion de entrada; quien llama decide que hacer con las
     * que faltan (crearlas).
     */
    List<E> findByCodeIn(Collection<String> codes);
}
