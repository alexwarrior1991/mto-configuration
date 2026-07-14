package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeApplicationEvent;
import com.alejandro.mtoconfiguration.service.commons.event.EntityChangeOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MasterDataEntityChangedEventListener {

    private final MasterDataEventPublisher masterDataEventPublisher;
    private final MasterDataRepositoryResolver repositoryResolver;

    @EventListener
    public void onEntityChanged(EntityChangeApplicationEvent<? extends IEntity> event){
        IEntity entity = event.entity();

        if (!isPublishable(entity, event.operation())){
            return;
        }

        IEntity entityForMessaging = findEntityForMessaging(entity)
                .orElse(entity);

        MasterDataOperation operation = toMasterDataOperation(event.operation());

        masterDataEventPublisher.publish(entityForMessaging, operation);

        log.debug(
                "Master data event registered in outbox. entityType={}, entityId={}, operation={}",
                entityForMessaging.getClass().getSimpleName(),
                entityForMessaging.getId(),
                operation
        );
    }

    private boolean isPublishable(IEntity entity, EntityChangeOperation operation) {
        if (entity == null || entity.getId() == null || operation == null) {
            return false;
        }

        return entity.getClass().isAnnotationPresent(PublishMasterDataEvent.class);
    }

    private Optional<IEntity> findEntityForMessaging(IEntity entity) {

        Optional<JpaRepository<IEntity, Long>> repository = repositoryResolver.resolve(entity);

        if (repository.isEmpty()) {
            return Optional.of(entity);
        }

        JpaRepository<IEntity, Long> jpaRepository = repository.get();

        if (jpaRepository instanceof MessagingEntityGraphRepository<?> messagingRepository) {

            @SuppressWarnings("unchecked")
            MessagingEntityGraphRepository<IEntity> typedRepository =
                    (MessagingEntityGraphRepository<IEntity>) messagingRepository;

            return typedRepository.findByIdForMessaging(entity.getId());
        }

        return Optional.of(entity);
    }

    private MasterDataOperation toMasterDataOperation(EntityChangeOperation operation) {
        return switch (operation) {
            case CREATED -> MasterDataOperation.CREATED;
            case UPDATED -> MasterDataOperation.UPDATED;
            case DELETED -> MasterDataOperation.DELETED;
        };
    }

}
