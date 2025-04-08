package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.repository.StatisticsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private static final Logger log = LoggerFactory.getLogger(StatisticsServiceImpl.class);


    private final Random random = new Random();
    private final SitesList sites;

    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    //@Autowired
    private final SiteRepository siteRepository;

    @Override
    public StatisticsResponse getStatistics() {
//        String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
//        String[] errors = {
//                "Ошибка индексации: главная страница сайта не доступна",
//                "Ошибка индексации: сайт не доступен",
//                ""
//        };

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        total.setPages(pageRepository.findAll().size());
        total.setLemmas(lemmaRepository.findAll().size());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setStatus("В очереди на индексацию");
            item.setError("Ошибок нет");
//            int pages = random.nextInt(1_000);
//            int lemmas = pages * random.nextInt(1_000);
//            item.setStatus(statuses[i % 3]);
            Optional<SiteEntity> siteEntityOptional = siteRepository.findByName(site.getName());
            if (siteEntityOptional.isPresent()) {
                SiteEntity siteEntity = siteEntityOptional.get();
                item.setStatus(siteEntity.getStatus().toString());
                item.setError(siteEntity.getLastError());
                item.setStatusTime(siteEntity.getStatusTime().getTime());
                int pages = pageRepository.findAllBySiteId(siteEntity.getId()).size();
                int lemmas = lemmaRepository.findAllBySiteId(siteEntity.getId()).size();
                item.setPages(pages);
                item.setLemmas(lemmas);
            }
//            item.setError(errors[i % 3]);
//            item.setStatusTime(System.currentTimeMillis() -
//                    (random.nextInt(10_000)));
//            total.setPages(total.getPages() + pages);
//            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
