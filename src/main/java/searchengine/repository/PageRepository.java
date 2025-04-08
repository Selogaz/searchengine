package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    Optional<PageEntity> findByPathAndSiteId(String path, Integer id);

    @Transactional
    void deleteBySiteId(Integer siteId);

    List<PageEntity> findAllBySiteId(Integer id);

}
