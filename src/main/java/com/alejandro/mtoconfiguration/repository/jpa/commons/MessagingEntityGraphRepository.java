package com.alejandro.mtoconfiguration.repository.jpa.commons;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;

import java.util.Optional;

public interface MessagingEntityGraphRepository<E extends IEntity> {

    Optional<E> findByIdForMessaging(Long id);
}
