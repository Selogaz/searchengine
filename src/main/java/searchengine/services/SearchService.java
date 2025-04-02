package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaFrequencyAnalyzer;
import searchengine.config.SitesList;
import searchengine.dto.Response;
import searchengine.dto.search.SearchErrorResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchService implements SearchRepository {
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;

    @Autowired
    public SearchService(LemmaRepository lemmaRepository, PageRepository pageRepository, IndexRepository indexRepository,
                         SiteRepository siteRepository) {
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
    }

    public Response startSearch(String query, String url) {
        if (query == null || query.trim().isEmpty()) {
            SearchErrorResponse searchErrorResponse = new SearchErrorResponse("Задан пустой поисковый запрос");
            searchErrorResponse.setResult(false);
            return searchErrorResponse;
            //return new SearchErrorResponse("Задан пустой поисковый запрос");
        }
        List<SearchResult> searchResults = mainSearch(query, url);
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.setData(searchResults);
        searchResponse.setCount(searchResults.size());
        searchResponse.setResult(true);
        return searchResponse;
    }

private Set<Integer> findPages(Map<String, Integer> sortedLemmas, String url) {
    Set<Integer> resultPages = null;
    for (String lemma : sortedLemmas.keySet()) {
        List<IndexEntity> entries;
        if (url == null) {
            entries = indexRepository.findByLemmaId(sortedLemmas.get(lemma));
        } else {
            entries = indexRepository.findByLemmaIdAndSiteUrl(sortedLemmas.get(lemma), url);
        }

        Set<Integer> currentPages = entries.stream()
                .map(indexEntry -> indexEntry.getPage().getId())
                .collect(Collectors.toSet());

        if (resultPages == null) {
            resultPages = new HashSet<>(currentPages);
        } else {
            resultPages.retainAll(currentPages);
        }

        if (resultPages.isEmpty()) break;
    }

    return resultPages != null ? resultPages : Collections.emptySet();
}

    private List<SearchResult> mainSearch(String query, String url) {
        LemmaFrequencyAnalyzer frequencyAnalyzer = new LemmaFrequencyAnalyzer();
        Map<String, Integer> excludedLemmas = excludeLemmas(frequencyAnalyzer.frequencyMap(query), url);
        Map<String, Integer> sortedLemmas = sortLemmas(excludedLemmas);
        Set<Integer> resultPages = findPages(sortedLemmas, url);
        List<SearchResult> relevanceResults = calculateRelevance(resultPages, sortedLemmas);
        System.out.println(relevanceResults);
        return relevanceResults;
    }

    private List<SearchResult> calculateRelevance(Set<Integer> pageIds, Map<String, Integer> sortedLemmas) {
        Map<Integer, Float> relevanceMap = calculateRelevanceScores(pageIds, sortedLemmas);
        float maxRelevance = Collections.max(relevanceMap.values(), Float::compare);

        return pageIds.stream()
                .map(pageId -> createSearchResult(pageId, relevanceMap.get(pageId), maxRelevance, sortedLemmas))
                .toList();
    }

    private Map<Integer, Float> calculateRelevanceScores(Set<Integer> pageIds, Map<String, Integer> sortedLemmas) {
        Map<Integer, Float> relevanceMap = new HashMap<>();
        for (Integer pageId : pageIds) {
            float relevance = sortedLemmas.keySet().stream()
                    .map(lemma -> calculateLemmaRelevance(pageId, lemma, sortedLemmas.get(lemma)))
                    .reduce(0.0f, Float::sum);
            relevanceMap.put(pageId, relevance);
        }
        return relevanceMap;
    }

    private float calculateLemmaRelevance(Integer pageId, String lemma, Integer lemmaId) {
        return indexRepository.findByLemmaId(lemmaId).stream()
                .filter(entry -> entry.getPage().getId().equals(pageId))
                .map(IndexEntity::getRank)
                .reduce(0.0f, Float::sum);
    }

    private SearchResult createSearchResult(Integer pageId, float relevance, float maxRelevance, Map<String, Integer> sortedLemmas) {
        PageEntity page = pageRepository.findById(pageId).orElseThrow();

        float normalizedRelevance = relevance / maxRelevance;
        String snippet = generateSnippet(page.getContent(), sortedLemmas.keySet());

        return new SearchResult(
                page.getSite().getUrl().substring(0,page.getSite().getUrl().length() - 1),
                page.getSite().getName(),
                page.getPath(),
                extractTitleFromContent(page.getContent()),
                snippet,
                normalizedRelevance
        );
    }

//    private List<SearchResult> calculateRelevance(Set<Integer> pageIds, Map<String, Integer> sortedLemmas) {
//        Map<Integer, Float> relevanceMap = new HashMap<>();
//        float maxRelevance = 0;
//
//        for (Integer pageId : pageIds) {
//            float relevance = 0;
//            for (String lemma : sortedLemmas.keySet()) {
//                relevance += indexRepository.findByLemmaId(sortedLemmas.get(lemma)).stream()
//                        .filter(entry -> entry.getPage().getId().equals(pageId))
//                        .map(IndexEntity::getRank)
//                        .reduce(0.0f, Float::sum);
//            }
//            relevanceMap.put(pageId, relevance);
//            maxRelevance = Math.max(maxRelevance, relevance);
//        }
//
//        float finalMaxRelevance = maxRelevance;
//        return pageIds.stream()
//                .map(pageId -> {
//                    PageEntity page = pageRepository.findById(pageId).orElseThrow();
//                    float relevance = relevanceMap.get(pageId) / finalMaxRelevance;
//                    String snippet = generateSnippet(page.getContent(), sortedLemmas.keySet());
//                    return new SearchResult(
//                            page.getSite().getUrl().substring(0,page.getSite().getUrl().length() - 1),
//                            page.getSite().getName(),
//                            page.getPath(),
//                            extractTitleFromContent(page.getContent()),
//                            snippet,
//                            relevance
//                    );
//                })
//                .toList();
//    }

    private String extractTitleFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return "No Title";
        }

        try {
            Document document = Jsoup.parse(content);
            String title = document.title();
            return title != null && !title.trim().isEmpty() ? title.trim() : "No Title";
        } catch (Exception e) {
            System.err.println("Error extracting title: " + e.getMessage());
            return "No Title";
        }
    }

    private String generateSnippet(String content, Set<String> lemmas) {
        int snippetLength = 250;

        Document document = Jsoup.parse(content);
        String text = document.body().text();

        int matchIndex = -1;
        for (String lemma : lemmas) {
            Pattern pattern = Pattern.compile("(?i)" + lemma);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                matchIndex = matcher.start();
                break;
            }
        }

        if (matchIndex == -1) {
            return text.substring(0, Math.min(text.length(), snippetLength));
        }

        int start = Math.max(0, matchIndex - snippetLength / 2);
        int end = Math.min(text.length(), matchIndex + snippetLength / 2);

        String snippet = text.substring(start, end);

        for (String lemma : lemmas) {
            snippet = snippet.replaceAll("(?i)" + lemma, "<b>$0</b>");
        }

        return snippet;
    }

    private Map<String, Integer> excludeLemmas(Map<String, Integer> queryLemmas, String url) {
        Optional<SiteEntity> siteEntity = siteRepository.findByUrl(url);
        List<LemmaEntity> siteLemmasEntities;
        int pageCount;
        if (siteEntity.isPresent()) {
            Integer siteId = siteEntity.get().getId();
            siteLemmasEntities = lemmaRepository.findAllBySiteId(siteId);
            pageCount = pageRepository.findAllBySiteId(siteId).size();
        } else {
            siteLemmasEntities = lemmaRepository.findAll();
            pageCount = pageRepository.findAll().size();
        }


        double threshold = pageCount * 0.8;
        Map<String, Integer> lemmaFreqMap = new HashMap<>();
        siteLemmasEntities.forEach(lemmaEntity -> lemmaFreqMap.put(lemmaEntity.getLemma(), lemmaEntity.getFrequency()));
        return queryLemmas.entrySet().stream()
                .filter(entry -> {
                    Integer globalFreq = lemmaFreqMap.get(entry.getKey());
                    return globalFreq != null && globalFreq <= threshold;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Integer> sortLemmas(Map<String, Integer> lemmas) {
        return lemmas.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldVal, newVal) -> oldVal,
                        LinkedHashMap::new
                ));
    }
}
