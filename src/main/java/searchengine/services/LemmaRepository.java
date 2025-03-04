package searchengine.services;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    @Transactional
    //@Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LemmaEntity> findByLemmaAndSite(String lemmaText, SiteEntity site);
}
