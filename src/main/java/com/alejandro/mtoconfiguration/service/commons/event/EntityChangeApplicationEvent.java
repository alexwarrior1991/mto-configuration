package com.alejandro.mtoconfiguration.service.commons.event;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;

public record EntityChangeApplicationEvent<E extends IEntity>(
        E entity,
        EntityChangeOperation operation
) {
}
