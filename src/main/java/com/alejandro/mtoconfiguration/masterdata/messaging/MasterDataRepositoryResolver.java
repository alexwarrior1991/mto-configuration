package com.alejandro.mtoconfiguration.masterdata.messaging;

import com.alejandro.mtoconfiguration.entity.commons.IEntity;
import com.alejandro.mtoconfiguration.repository.jpa.commons.MessagingEntityGraphRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resuelve el repositorio con el que cargar una entidad para publicarla.
 * <p>
 * Cada entidad publicable tiene DOS repositorios Spring Data para el mismo tipo de
 * dominio: el suyo ({@code CantileverRepository}, que implementa
 * {@code MessagingEntityGraphRepository}) y su repositorio de busqueda
 * ({@code CantileverCriteriaSearchRepository}). {@code Repositories} indexa por tipo de
 * dominio en un mapa, de modo que con dos candidatos gana el ultimo registrado y cual
 * sea eso depende del orden de escaneo del classpath.
 * <p>
 * Eso no es un detalle interno: si sale el de busqueda,
 * {@code MasterDataEntityChangedEventListener} no reconoce un
 * {@code MessagingEntityGraphRepository}, se salta el {@code @EntityGraph} de
 * {@code findByIdForMessaging} y publica la entidad tal cual llego en el evento, con
 * sus relaciones perezosas sin inicializar. Por eso aqui se busca primero, de forma
 * explicita, el repositorio de mensajeria del tipo, y solo si no existe se cae a la
 * resolucion generica.
 */
@Component
public class MasterDataRepositoryResolver {

    private final ApplicationContext applicationContext;
    private final Repositories repositories;

    /** Los beans de repositorio no cambian en caliente: basta con resolver una vez por tipo. */
    private final Map<Class<?>, Optional<JpaRepository<IEntity, Long>>> cache = new ConcurrentHashMap<>();

    public MasterDataRepositoryResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.repositories = new Repositories(applicationContext);
    }

    public Optional<JpaRepository<IEntity, Long>> resolve(IEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }

        Optional<JpaRepository<IEntity, Long>> cached = cache.get(entity.getClass());

        if (cached != null) {
            return cached;
        }

        // Fuera del mapa a proposito, y no con computeIfAbsent: resolver implica pedir
        // beans, y hacerlo dentro del lock de la entrada arriesga un "recursive update"
        // si esa inicializacion acabase pidiendo otra resolucion. Que dos hilos calculen
        // a la vez no molesta: los repositorios son singletons y el resultado es el mismo.
        Optional<JpaRepository<IEntity, Long>> resolved =
                findMessagingRepository(entity).or(() -> findAnyRepository(entity));

        cache.put(entity.getClass(), resolved);

        return resolved;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Optional<JpaRepository<IEntity, Long>> findMessagingRepository(IEntity entity) {
        for (MessagingEntityGraphRepository repository :
                applicationContext.getBeansOfType(MessagingEntityGraphRepository.class).values()) {

            if (repository instanceof JpaRepository<?, ?> && manages(repository, entity)) {
                return Optional.of((JpaRepository<IEntity, Long>) repository);
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<JpaRepository<IEntity, Long>> findAnyRepository(IEntity entity) {
        return repositories.getRepositoryFor(entity.getClass())
                .filter(JpaRepository.class::isInstance)
                .map(repository -> (JpaRepository<IEntity, Long>) repository);
    }

    private boolean manages(Object repository, IEntity entity) {
        Class<?> domainType = messagingDomainType(repository);

        // isInstance y no equals: la entidad puede llegar como proxy de Hibernate, que
        // es una subclase del tipo de dominio y no el tipo exacto.
        return domainType != null && domainType.isInstance(entity);
    }

    /**
     * El bean es un proxy, asi que el tipo de dominio se saca de la interfaz de
     * repositorio que declara el {@code MessagingEntityGraphRepository<E>}.
     */
    private Class<?> messagingDomainType(Object repository) {
        for (Class<?> repositoryInterface : ClassUtils.getAllInterfaces(repository)) {
            Class<?> domainType = GenericTypeResolver.resolveTypeArgument(
                    repositoryInterface, MessagingEntityGraphRepository.class);

            if (domainType != null) {
                return domainType;
            }
        }

        return null;
    }
}
