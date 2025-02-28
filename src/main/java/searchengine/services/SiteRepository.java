package searchengine.services;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import javax.persistence.LockModeType;
import javax.persistence.QueryHint;
import java.util.List;
import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    Optional<SiteEntity> findByUrl(String url);
    @QueryHints({@QueryHint(name = "org.hibernate.cacheable", value = "false")})
    Optional<SiteEntity> findById(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM site s WHERE s.id = :id")
    Optional<SiteEntity> findByIdWithLock(@Param("id") Integer id);

    Optional<SiteEntity> findByName(String name);
    List<SiteEntity> findByStatus(Status status);

    @Transactional
    void delete(SiteEntity entity);
}
