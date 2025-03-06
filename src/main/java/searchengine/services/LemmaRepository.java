package searchengine.services;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Transactional
    //@Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LemmaEntity> findByLemmaAndSite(String lemmaText, SiteEntity site);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO lemma (lemma, site_id, frequency) 
        VALUES (:lemma, :siteId, 1) 
        ON DUPLICATE KEY UPDATE frequency = frequency + 1
        """)
    void upsertLemma(@Param("lemma") String lemma, @Param("siteId") int siteId);
}
