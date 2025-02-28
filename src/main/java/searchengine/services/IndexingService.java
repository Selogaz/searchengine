package searchengine.services;

import lombok.RequiredArgsConstructor;
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
import searchengine.services.parser.SiteMap;
import searchengine.services.parser.SiteMapRecursiveAction;

import javax.persistence.NonUniqueResultException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

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

    private ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
    List<ForkJoinTask<?>> tasks = new CopyOnWriteArrayList<>();

    private ThreadPoolExecutor executor;

    private AtomicBoolean isStopped = new AtomicBoolean(true);
    private SiteMapRecursiveAction task;

    private final String INDEXING_ALREADY_STARTED = "Индексация уже запущена";
    private final String INDEXING_STOPPED_BY_USER = "Индексация остановлена пользователем";

    public Response startFullIndexing() {
        Response response;
        isStopped.set(false);
            if (isIndexingStarted()) {
                ErrorResponse errorResponse = new ErrorResponse(INDEXING_ALREADY_STARTED);
                log.warn(INDEXING_ALREADY_STARTED);
                errorResponse.setResult(false);
                response = errorResponse;
            } else {
                log.info("Запущена индексация");
                forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
                executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(3);
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
            task.stopRecursiveAction();
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
                    indexPage(siteEntity.getId());//запуск задачи
                }).join();
                if (isStopped.get()) {
                    siteEntity.setStatus(Status.FAILED);
                    return;
                }
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
        if (site.getUrl().contains("www.")) {
            siteEntity.setUrl(site.getUrl().replace("www.",""));
        }
        siteEntity.setLastError(null);
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(Date.from(Instant.now()));
        return siteEntity;
    }

    @Transactional
    private void indexPage(Integer siteId) {
        if (isStopped.get()) {
            task.stopRecursiveAction();
            return;
        }
        log.info("Запуск обхода страниц для сайта с ID: {}", siteId);
        try {
            SiteEntity attachedSite = siteRepository.findById(siteId)
                    .orElseThrow(() -> new RuntimeException("Сайт с ID " + siteId + " не найден"));

            task = createTask(attachedSite);
            forkJoinPool.invoke(task);
            if (!task.getPageBuffer().isEmpty()) {
                pageRepository.saveAll(task.getPageBuffer());
                task.getPageBuffer().clear();
            }
            attachedSite.setStatusTime(Date.from(Instant.now()));
            siteRepository.save(attachedSite);

        } catch (Exception e) {
            if (!isStopped.get()) {
                //log.error("Ошибка при обходе страницы: {}", path, e);
                log.error("Ошибка при обходе страницы: ", e);
            }
        }
    }

    private SiteMapRecursiveAction createTask(SiteEntity attachedSite) {
        Set<String> linksPool = ConcurrentHashMap.newKeySet();
        Set<PageEntity> sitePageBuffer = ConcurrentHashMap.newKeySet();
        SiteMap siteMap = new SiteMap(attachedSite.getUrl());
        return new SiteMapRecursiveAction(siteMap, attachedSite, pageRepository, isStopped,
                sitePageBuffer, linksPool);
    }

    private boolean isIndexingStarted() {
        List<SiteEntity> savedSites = siteRepository.findAll();
        for (SiteEntity site : savedSites) {
            if (site.getStatus().equals(Status.INDEXING)) {
                return true;
            }
        }
        return false;
    }

    public Response stopIndexing() {
        Response response;
        if (!isIndexingStarted()) {
            ErrorResponse errorResponse = new ErrorResponse("Индексация не запущена");
            log.warn("Индексация не запущена");
            errorResponse.setResult(true);
            response = errorResponse;
            return response;
        }

        updateSiteStatuses(Status.INDEXING,Status.FAILED, INDEXING_STOPPED_BY_USER);
        isStopped.set(true);
        stopForkJoinPool();

        if (executor != null) {
            executor.shutdownNow();
            log.info(executor.isShutdown() ? "ThreadPoolExecutor успешно остановлен." : "ThreadPoolExecutor не был остановлен.");
        }
        ErrorResponse successResponse = new ErrorResponse(INDEXING_STOPPED_BY_USER);
        log.info(INDEXING_STOPPED_BY_USER);
        successResponse.setResult(true);
        response = successResponse;
        return response;
    }

    @Transactional
    private void updateSiteStatuses(Status from,Status to, String note) {
        List<SiteEntity> indexingSites = siteRepository.findByStatus(from);
        for (SiteEntity site : indexingSites) {
            site.setStatus(to);
            site.setLastError(note);
            site.setStatusTime(Date.from(Instant.now()));
            siteRepository.save(site);
        }
    }

    private void stopForkJoinPool() {
        for (ForkJoinTask<?> task : tasks) {
            task.cancel(true);
        }
        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow();
            try {
                if (!forkJoinPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.error("ForkJoinPool не завершился за 3 секунды");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        tasks.clear();
        log.info(forkJoinPool.isShutdown() ? "ForkJoinPool успешно остановлен." : "ForkJoinPool не был остановлен.");
    }

}