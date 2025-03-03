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
    Optional<PageEntity> findByPathAndSiteId(String path, Integer id);

    @Transactional
    void deleteBySiteId(Integer siteId);

    //Optional<PageEntity> findByUrl(String url);
}
