package searchengine.services;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    @Transactional
    List<IndexEntity> findByPage(PageEntity page);

    List<IndexEntity> findByLemmaId(Integer lemmaId);

    @Query("SELECT i FROM IndexEntity i " +
            "JOIN i.page p " +
            "JOIN p.site s " +
            "WHERE i.lemmaId = :lemmaId AND s.url = :url")
    List<IndexEntity> findByLemmaIdAndSiteUrl(@Param("lemmaId") Integer lemmaId, @Param("url") String url);
}
