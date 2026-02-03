package com.alejandro.mtoconfiguration.repository.jpa.lov.commons;

import com.alejandro.mtoconfiguration.entity.lov.commons.Lov;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface LovRepository<E extends Lov> extends JpaRepository<E, Long>, QuerydslPredicateExecutor<E> {

    E findByCode(String code);
    Page<E> findByCodeContainsIgnoreCase(String code, Pageable pageable );
    List<E> findByCodeLikeIgnoreCaseOrDescriptionLikeIgnoreCase(String code, String description );
}
