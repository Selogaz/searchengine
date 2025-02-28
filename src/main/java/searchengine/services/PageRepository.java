package searchengine.services;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

import javax.persistence.criteria.CriteriaBuilder;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
//    @Query("SELECT p FROM PageEntity p WHERE p.site_id.id = :siteId AND p.path = :path")
//    Optional<PageEntity> findBySiteIdAndPath(@Param("siteId") Integer siteId, @Param("path") String path);
    Optional<PageEntity> findBySiteIdAndPath(Integer id, String path);

    @Transactional
    void deleteBySiteId(Integer siteId);
}
