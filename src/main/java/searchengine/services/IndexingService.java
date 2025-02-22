package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.Response;
import searchengine.dto.indexing.ErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingService {
    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Value("${indexing-settings.user-agent}")
    private String userAgent;

    @Value("${indexing-settings.referrer}")
    private String referrer;

    @Autowired
    private final SitesList sites;

    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    List<ForkJoinTask<?>> tasks = new CopyOnWriteArrayList<>();

    private ThreadPoolExecutor executor;

    private static AtomicBoolean isStopped = new AtomicBoolean(false);

    public Response startFullIndexing() {
        Response response = null;
        isStopped.set(false);
            if (indexingStatusCheck(Status.INDEXING)) {
                ErrorResponse errorResponse = new ErrorResponse("Индексация уже запущена");
                log.warn("Индексация уже запущена");
                errorResponse.setResult(false);
                response = errorResponse;
            } else {
                log.info("Запущена индексация");
                executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
                executor.setMaximumPoolSize(Runtime.getRuntime().availableProcessors());
                executor.execute(this::indexSite);
                IndexingResponse okResponse = new IndexingResponse();
                okResponse.setResult(true);
                response = okResponse;
            }
        return response;
    }

    public void indexSite() {
        if (isStopped.get()) {
            return;
        }
        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            Optional<SiteEntity> existingSite = siteRepository.findByUrl(site.getUrl());
            if (existingSite.isPresent()) {
                pageRepository.deleteBySiteId(existingSite.get().getId());
                siteRepository.delete(existingSite.get());
            }
            SiteEntity siteEntity = createSiteEntity(site);
            siteRepository.save(siteEntity);
            siteRepository.flush();

            try {
                forkJoinPool.submit(() -> {
                    indexPage(siteEntity.getId(), "/");//запуск задачи
                }).join();
                siteEntity.setStatus(Status.INDEXED);
                siteEntity.setStatusTime(Date.from(Instant.now()));
            } catch (Exception e) {
                siteEntity.setStatus(Status.FAILED);
                siteEntity.setLastError(e.getMessage());
                //log.warn("Статус индексации FAILED: {}", String.valueOf(e));
                return;
            } finally {
                siteEntity.setStatusTime(Date.from(Instant.now()));
                siteRepository.save(siteEntity);
            }
            if (siteEntity.getStatus().equals(Status.INDEXED) && !isStopped.get()) {
                log.info("Индексация завершена для сайта: {}", site.getUrl());
            }
        }
    }

    private SiteEntity createSiteEntity(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setLastError(null);
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(Date.from(Instant.now()));
        return siteEntity;
    }

    @Transactional
    private void indexPage(Integer siteId, String path) {
        if (isStopped.get()) {
            return;
        }
        log.info("Запуск обхода страниц для сайта с ID: {}", siteId);
        try {
            SiteEntity attachedSite = siteRepository.findById(siteId)
                    .orElseThrow(() -> new RuntimeException("Сайт с ID " + siteId + " не найден"));
            String fullUrl = attachedSite.getUrl() + path;
            Document doc = getDocument(fullUrl);
            PageEntity page = createPageEntity(attachedSite, path, doc);

            pageRepository.save(page);
            attachedSite.setStatusTime(Date.from(Instant.now()));
            siteRepository.save(attachedSite);

            ConcurrentSkipListSet<String> newPaths = getLinks(doc, attachedSite.getUrl());
            for (String newPath : newPaths) {
                tasks.add(ForkJoinTask.adapt(() -> indexPage(siteId, newPath)));//создание задачи
            }
            ForkJoinTask.invokeAll(tasks);//запуск на параллельное выполнение
            Thread.sleep(1000);
        } catch (Exception e) {
            if (!isStopped.get()) {
                log.error("Ошибка при обходе страницы: {}", path, e);
            }
        }
    }

    public ConcurrentSkipListSet<String> getLinks(Document doc, String url) {
        Elements links = doc.select("a[href]");
        ConcurrentSkipListSet<String> newPaths = new ConcurrentSkipListSet<>();
        for (Element link : links) {
            String nextUrl = link.attr("abs:href");
            if (nextUrl.startsWith(url) && isLink(nextUrl) && !isFile(nextUrl)) {
                String newPath = nextUrl.substring(url.length());
                newPaths.add(newPath);
            }
        }
        return newPaths;
    }

    public Document getDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(5000)
                .get();
    }

    public PageEntity createPageEntity(SiteEntity attachedSite, String path, Document doc) {
        String content = doc.html();
        int status = doc.connection().response().statusCode();

        PageEntity page = new PageEntity();
        page.setSite(attachedSite);
        page.setPath(path);
        page.setCode(status);
        page.setContent(content);
        return page;
    }

    private static boolean isLink(String link) {
        String regex = "(^https:\\/\\/)(?:[^@\\/\\n]+@)?(?:www\\.)?([^:\\/\\n]+)";
        return link.matches(regex);
    }

    private static boolean isFile(String link) {
        link = link.toLowerCase();
        return link.contains(".jpg")
                || link.contains(".jpeg")
                || link.contains(".png")
                || link.contains(".gif")
                || link.contains(".webp")
                || link.contains(".pdf")
                || link.contains(".eps")
                || link.contains(".xlsx")
                || link.contains(".doc")
                || link.contains(".pptx")
                || link.contains(".docx")
                || link.contains("?_ga");
    }

    public boolean indexingStatusCheck(Status status) {
        for (Site site : sites.getSites()) {
            Optional<SiteEntity> savedSite = siteRepository.findByName(site.getName());
            if (savedSite.isPresent() && savedSite.get().getStatus() == status) {
                return true;
            }
        }
        return false;
    }

//    public Response stopIndexing() {
//        Response response = null;
//            if (isIndexingStarted()) {
//                ErrorResponse errorResponse = new ErrorResponse("Индексация не запущена");
//                errorResponse.setResult(false);
//                response = errorResponse;
//            } else {
//                stopFJPool();
//                ErrorResponse errorResponse = new ErrorResponse("Индексация остановлена пользователем");
//                errorResponse.setResult(true);
//                response = errorResponse;
//            }
//
//        return response;
//    }

    public Response stopIndexing() {
        Response response = null;
        if (!indexingStatusCheck(Status.INDEXING)) {
            ErrorResponse errorResponse = new ErrorResponse("Индексация не запущена");
            log.warn("Индексация не запущена");
            errorResponse.setResult(true);
            response = errorResponse;
            return response;
        }

        updateSiteStatuses(Status.INDEXING,Status.FAILED);
        isStopped.set(true);
        stopForkJoinPool();
        if (executor != null) {
            executor.shutdownNow();
            log.info(executor.isShutdown() ? "ThreadPoolExecutor успешно остановлен." : "ThreadPoolExecutor не был остановлен.");
        }


        ErrorResponse successResponse = new ErrorResponse("Индексация остановлена пользователем");
        log.info("Индексация остановлена пользователем");
        successResponse.setResult(true);
        response = successResponse;
        return response;
    }

    @Transactional
    private void updateSiteStatuses(Status from,Status to) {
        List<SiteEntity> indexingSites = siteRepository.findByStatus(from);
        for (SiteEntity site : indexingSites) {
            site.setStatus(to);
            site.setStatusTime(Date.from(Instant.now()));
            siteRepository.save(site);
        }
    }

    public void stopForkJoinPool() {
        for (ForkJoinTask<?> task : tasks) {
            task.cancel(true);
        }
        forkJoinPool.shutdownNow();
        tasks.clear();;
        log.info(forkJoinPool.isShutdown() ? "ForkJoinPool успешно остановлен." : "ForkJoinPool не был остановлен.");
    }

}