package searchengine.services.parser;


import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.LemmaFrequencyAnalyzer;
import searchengine.config.IndexingConfig;
import searchengine.dto.indexing.SiteMap;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import searchengine.services.IndexRepository;
import searchengine.services.IndexingService;
import searchengine.services.LemmaRepository;
import searchengine.services.PageRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class SiteMapRecursiveAction extends RecursiveAction {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private SiteMap siteMap;
    private final Set<String> linksPool;
    @Getter
    private final Set<PageEntity> pageBuffer;

    private final SiteEntity siteEntity;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final AtomicBoolean isStopped;

    private static final Set<String> FILE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", "?_ga","pptx","xlsx","eps",".webp",".png", ".gif", ".pdf", ".doc", ".docx"
    );

    private String userAgent;
    private String referrer;
    private Integer timeout;

    public SiteMapRecursiveAction(SiteMap siteMap, SiteEntity siteEntity, PageRepository pageRepository,
                                  AtomicBoolean isStopped, Set<PageEntity> pageBuffer, Set<String> linksPool,
                                  IndexingConfig indexingConfig, IndexRepository indexRepository, LemmaRepository lemmaRepository) {
        this.siteMap = siteMap;
        this.linksPool = linksPool;
        this.pageBuffer = pageBuffer;
        this.siteEntity = siteEntity;
        this.pageRepository = pageRepository;
        this.isStopped = isStopped;
        this.userAgent = indexingConfig.getUserAgent();
        this.referrer = indexingConfig.getReferrer();
        this.timeout = indexingConfig.getTimeout();
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
    }

    @Override
    protected void compute() {
        if (isStopped.get()) {
            return;
        }
        linksPool.add(siteMap.getUrl());
        Document doc = getDocumentByUrl(siteMap.getUrl());
        savePage(doc);
        Set<String> links;
        try {
            links = getLinks(doc);
        } catch (NullPointerException e) {
            log.error("failed to get links");
            return;
        }

        List<SiteMapRecursiveAction> taskList = new ArrayList<>();
        for (String link : links) {
            if (isStopped.get()) break;
            if (linksPool.add(link)) {
                SiteMap childSiteMap = new SiteMap(link);
                siteMap.addChildren(childSiteMap);
                SiteMapRecursiveAction task = new SiteMapRecursiveAction(childSiteMap, siteEntity, pageRepository,
                        isStopped,pageBuffer,linksPool, new IndexingConfig(userAgent,referrer,timeout), indexRepository,
                        lemmaRepository);
                task.fork();
                taskList.add(task);
            }
        }
        invokeAll(taskList);
    }

    public void stopRecursiveAction() {
        isStopped.set(true);
    }

    public Set<String> getLinks(Document doc) throws NullPointerException{
        return doc.select("a[href]").stream()
                .map(link -> link.attr("abs:href"))
                .filter(url -> !url.contains("#"))
                .filter(url -> url.startsWith(siteMap.getDomain()))
                .filter(url -> !isFile(url))
                .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet));
    }

    private static boolean isFile(String link) {
        String lowerUrl = link.toLowerCase();
        return FILE_EXTENSIONS.stream().anyMatch(lowerUrl::contains);
    }

    public Document getDocumentByUrl(String url) {
        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .ignoreHttpErrors(true)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(timeout)
                    .get();
        } catch (IOException e) {
            log.error("IOException -> failed to get Document");
        } catch (NullPointerException e) {
            log.error("doc is null");
        }
        return doc;
    }


    public void savePage(Document doc) {
        if (doc == null) {
            log.warn("Failed to save page");
            return;
        }
        String content = "";
        try {
            //content = doc.body().text();
            content = doc.html();
        } catch (Exception e) {
            log.warn("Ошибка при получении контекста страницы: {}", siteMap.getUrl());
        }
        String path = siteMap.getUrl().substring(siteMap.getDomain().length());
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        Integer statusCode = doc.connection().response().statusCode();
        if (statusCode >= 400) {
            log.warn("Page status code is 4xx or 5xx");
            return;
        }

        PageEntity page = new PageEntity();
        page.setSite(siteEntity);
        page.setPath(path);
        page.setCode(statusCode);
        page.setContent(content);
        pageRepository.save(page);
        processPageContent(page);
//        log.info("Domain: {}", siteMap.getDomain());
//        log.info("URL: {}", siteMap.getUrl());
//        log.info("Path: {}", path);
    }

    @Transactional
    protected void processPageContent(PageEntity page) {
        LemmaFrequencyAnalyzer frequencyAnalyzer = new LemmaFrequencyAnalyzer();
        String text = frequencyAnalyzer.removeHtmlTags(page.getContent());
        Map<String, Integer> lemmas = frequencyAnalyzer.frequencyMap(text);
        updateLemmasAndIndices(page, lemmas);
        //log.info("индексы и леммы обновлены для страницы: {}", page.getId());
    }

    @Transactional
    protected void updateLemmasAndIndices(PageEntity page, Map<String, Integer> lemmas) {
        List<IndexEntity> indices = new ArrayList<>();
        int siteId = page.getSite().getId();

        lemmas.forEach((lemmaText, rank) -> {
            lemmaRepository.upsertLemma(lemmaText, siteId);
            LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite())
                    .orElseThrow();
            IndexEntity index = new IndexEntity();
            index.setPage(page);
            index.setLemmaId(lemma.getId());
            index.setRank((float) rank);
            indices.add(index);
        });
        indexRepository.saveAll(indices);
    }

}

