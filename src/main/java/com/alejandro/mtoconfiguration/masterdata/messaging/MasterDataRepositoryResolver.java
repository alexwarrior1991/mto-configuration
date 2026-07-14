package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MasterDataRepositoryResolver {

    private final Repositories repositories;

    public MasterDataRepositoryResolver(ApplicationContext applicationContext) {
        this.repositories = new Repositories(applicationContext);
    }

    @SuppressWarnings("unchecked")
    public Optional<JpaRepository<IEntity, Long>> resolve(IEntity entity){
        if (entity == null) {
            return Optional.empty();
        }

        return repositories.getRepositoryFor(entity.getClass())
                .filter(JpaRepository.class::isInstance)
                .map(repository -> (JpaRepository<IEntity, Long>) repository);
    }
}
