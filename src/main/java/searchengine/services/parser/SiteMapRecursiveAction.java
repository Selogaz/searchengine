package searchengine.services.parser;


import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import searchengine.config.IndexingConfig;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import searchengine.services.IndexingService;
import searchengine.services.PageRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final AtomicBoolean isStopped;

    private static final Set<String> FILE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", "?_ga","pptx","xlsx","eps",".webp",".png", ".gif", ".pdf", ".doc", ".docx"
    );

    //@Value("${indexing-settings.user-agent}")
    private String userAgent;

    //@Value("${indexing-settings.referrer}")
    private String referrer;

    public SiteMapRecursiveAction(SiteMap siteMap, SiteEntity siteEntity, PageRepository pageRepository,
                                  AtomicBoolean isStopped, Set<PageEntity> pageBuffer, Set<String> linksPool,
                                  IndexingConfig indexingConfig) {
        this.siteMap = siteMap;
        this.linksPool = linksPool;
        this.pageBuffer = pageBuffer;
        this.siteEntity = siteEntity;
        this.pageRepository = pageRepository;
        this.isStopped = isStopped;
        this.userAgent = indexingConfig.getUserAgent();
        this.referrer = indexingConfig.getReferrer();
    }

    @Override
    protected void compute() {
        if (isStopped.get()) {
            //log.info("Задача остановлена для: {}", siteMap.getUrl());
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
                        isStopped,pageBuffer,linksPool, new IndexingConfig(userAgent,referrer));
                task.fork();
                taskList.add(task);
            }
        }
        invokeAll(taskList);

//        for (SiteMapRecursiveAction task : taskList) {
//            if (isStopped.get()) {
//                task.cancel(true); // Отмена незавершенных задач
//            } else {
//                task.join();
//            }
//        }
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
                    .timeout(30 * 1000)
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
            content = doc.body().text();
        } catch (Exception e) {
            log.warn("Ошибка при получении контекста страницы: {}", siteMap.getUrl());
        }

        String title = "";
        try {
            title = doc.title();
        } catch (Exception e) {
            log.warn("Ошибка при получении заголовка страницы: {}", siteMap.getUrl());
        }

        String path = siteMap.getUrl().substring(siteMap.getDomain().length());
        if (!path.endsWith("/")) {
            path = path + "/";
        }

        Integer statusCode = doc.connection().response().statusCode();

        PageEntity page = new PageEntity();
        page.setSite(siteEntity);
        page.setPath(path);
        page.setCode(statusCode);
        page.setContent(content);

        pageBuffer.add(page);
        synchronized (pageBuffer) {
            if (pageBuffer.size() >= 100) {
                pageRepository.saveAll(pageBuffer);
                pageBuffer.clear();
                log.info("100 страниц сохранены в БД");
            }
        }


//        log.info("Domain: {}", siteMap.getDomain());
//        log.info("URL: {}", siteMap.getUrl());
//        log.info("Path: {}", path);

    }
}

