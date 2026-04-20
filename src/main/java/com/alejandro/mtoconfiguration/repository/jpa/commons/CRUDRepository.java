package com.alejandro.mtoconfiguration.repository.jpa.commons;

import com.alejandro.mtoconfiguration.entity.commons.CRUDEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface CRUDRepository<E extends CRUDEntity> extends JpaRepository<E, Long>, QuerydslPredicateExecutor<E> {
}
