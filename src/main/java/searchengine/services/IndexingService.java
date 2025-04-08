package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.LemmaFrequencyAnalyzer;
import searchengine.config.IndexingConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.Response;
import searchengine.dto.indexing.ErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.dto.indexing.SiteMap;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.parser.SiteMapRecursiveAction;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingService {
    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final IndexingConfig indexingConfig;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sites;

    private ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors() * 2);
    List<ForkJoinTask<?>> tasks = new CopyOnWriteArrayList<>();
    private ThreadPoolExecutor executor;
    private AtomicBoolean isStopped = new AtomicBoolean(true);
    private SiteMapRecursiveAction task;

    private final String INDEXING_ALREADY_STARTED = "Индексация уже запущена";
    private final String INDEXING_STOPPED_BY_USER = "Индексация остановлена пользователем";
    private final String INDEXING_NOT_STARTED = "Индексация не запущена";
    private final String PAGE_OUT_OF_CONFIG = "Данная страница находится за пределами сайтов, " +
            "указанных в конфигурационном файле";

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
                indexSite();
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
        for (int i = 0; i < sitesList.size();i++) {
            int finalI = i;
            new Thread(() -> processSite(sitesList.get(finalI))).start();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    private void processSite(Site site) {
        synchronized (site.getUrl().intern()) {
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
                    indexPage(siteEntity.getId());
                }).join();
                if (isStopped.get()) {
                    siteEntity.setStatus(Status.FAILED);
                    return;
                }
                siteEntity.setStatus(Status.INDEXED);
                siteEntity.setStatusTime(Date.from(Instant.now()));
            } catch (Exception e) {
                siteEntity.setStatus(Status.FAILED);
                siteEntity.setStatusTime(Date.from(Instant.now()));
                siteEntity.setLastError(e.getMessage());
                return;
            } finally {
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
                log.error("Ошибка при обходе страницы: ", e);
            }
        }
    }

    public Response addOrUpdatePage(String url) {
        Response response;
        if (isSiteExistsInConfig(url)) {
            log.info("Страница принадлежит сайту из конфигурации! Обработка...");
            addOnePage(url);
            IndexingResponse okResponse = new IndexingResponse();
            okResponse.setResult(true);
            response = okResponse;
            log.info("Страница успешно добавлена/обновлена");
        } else {
            ErrorResponse errorResponse = new ErrorResponse(PAGE_OUT_OF_CONFIG);
            log.warn(PAGE_OUT_OF_CONFIG);
            errorResponse.setResult(false);
            response = errorResponse;
        }
        return response;
    }

    @Transactional
    public void addOnePage(String url) {
        Site siteConfig = findSiteConfigByUrl(url);
        SiteEntity siteEntity;
        String path = extractPath(url,siteConfig.getUrl());
        PageDownloadResult downloadResult = downloadPage(url);
        if (downloadResult.statusCode >= 400) {
            log.error("Страница вернула ошибку: {}", downloadResult.statusCode);
            return;
        }
        Optional<SiteEntity> findedSiteEntity = siteRepository.findByName(siteConfig.getName());
        if (findedSiteEntity.isEmpty()) {
            siteEntity = createSiteEntity(siteConfig);
            siteRepository.save(siteEntity);
            PageEntity pageEntity = savePage(siteEntity, path, downloadResult);
            processPageContent(pageEntity);
        } else {
            deleteExistingPage(findedSiteEntity.get(), path);
            PageEntity pageEntity = savePage(findedSiteEntity.get(), path, downloadResult);
            processPageContent(pageEntity);
        }
    }

    private PageEntity savePage(SiteEntity siteEntity, String path, PageDownloadResult result) {
        PageEntity page = new PageEntity();
        page.setSite(siteEntity);
        page.setPath(path);
        page.setContent(result.content);
        page.setCode(result.statusCode);
        return pageRepository.save(page);
    }

    @Transactional
    protected void processPageContent(PageEntity page) {
        LemmaFrequencyAnalyzer frequencyAnalyzer = new LemmaFrequencyAnalyzer();
        String text = frequencyAnalyzer.removeHtmlTags(page.getContent());
        Map<String, Integer> lemmas = frequencyAnalyzer.frequencyMap(text);
        updateLemmasAndIndices(page, lemmas);
    }

    @Transactional
    protected void updateLemmasAndIndices(PageEntity page, Map<String, Integer> lemmas) {
        lemmas.forEach((lemmaText, rank) -> {
            LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite())
                    .orElseGet(() -> createNewLemma(lemmaText, page.getSite()));
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            IndexEntity index = new IndexEntity();
            index.setPage(page);
            index.setLemmaId(lemma.getId());
            index.setRank(Float.valueOf(rank));
            indexRepository.save(index);
        });
    }

    private LemmaEntity createNewLemma(String lemmaText, SiteEntity site) {
        LemmaEntity lemma = new LemmaEntity();
        lemma.setLemma(lemmaText);
        lemma.setSite(site);
        lemma.setFrequency(0);
        return lemma;
    }

    private record PageDownloadResult(int statusCode, String content) {}
    private PageDownloadResult downloadPage(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(indexingConfig.getUserAgent())
                    .referrer(indexingConfig.getReferrer())
                    .timeout(indexingConfig.getTimeout())
                    .get();
            return new PageDownloadResult(200, doc.html());
        } catch (IOException e) {
            return new PageDownloadResult(500, "");
        }
    }

    private Site findSiteConfigByUrl(String url) {
        return sites.getSites().stream()
                .filter(site -> url.startsWith(site.getUrl().replace("www.","")))
                .findFirst()
                .orElse(null);
    }

    public boolean isSiteExistsInConfig(String url) {
        for (Site site : sites.getSites()) {
            if (url.contains(site.getUrl().replace("www.",""))) {
                return true;
            }
        }
        return false;
    }

    private String extractPath(String pageUrl, String siteUrl) {
        siteUrl = siteUrl.replace("www.","");
        return "/" + pageUrl.substring(siteUrl.length()).replaceAll("^/", "");
    }

    private void deleteExistingPage(SiteEntity siteEntity, String path) {
        pageRepository.findByPathAndSiteId(path, siteEntity.getId())
                .ifPresent(this::deletePageData);
    }

    @Transactional
    protected void deletePageData(PageEntity page) {
        List<IndexEntity> indexes = indexRepository.findByPage(page);
        for (IndexEntity index : indexes) {
            Integer lemmaId = index.getLemmaId();
            Optional<LemmaEntity> lemmaOpt = lemmaRepository.findById(lemmaId);
            LemmaEntity lemma = null;
            if (lemmaOpt.isPresent()) {
                lemma = lemmaOpt.get();
            }
            if (lemma != null) {
                lemma.setFrequency(lemma.getFrequency() - 1);
                if (lemma.getFrequency() == 0) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemmaRepository.save(lemma);
                }
            } else {
                log.error("lemma is null ");
            }
            indexRepository.delete(index);
        }
        pageRepository.delete(page);
    }

    private SiteMapRecursiveAction createTask(SiteEntity attachedSite) {
        Set<String> linksPool = ConcurrentHashMap.newKeySet();
        Set<PageEntity> sitePageBuffer = ConcurrentHashMap.newKeySet();
        SiteMap siteMap = new SiteMap(attachedSite.getUrl());
        return new SiteMapRecursiveAction(siteMap, attachedSite, pageRepository, isStopped,
                sitePageBuffer, linksPool, indexingConfig, indexRepository, lemmaRepository);
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
            ErrorResponse errorResponse = new ErrorResponse(INDEXING_NOT_STARTED);
            log.warn(INDEXING_NOT_STARTED);
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