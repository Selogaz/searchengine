package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaFrequencyAnalyzer;
import searchengine.dto.Response;
import searchengine.dto.search.SearchErrorResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService implements SearchRepository {

    private final int MAX_ALLOWED_PAGES = 20;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final IndexRepository indexRepository;

    private final Map<String, Set<Integer>> lemmaIndex;

    public SearchResponse startSearch(String query) {
        Response response;
        SearchResponse searchResponse = new SearchResponse();
        if (true) {
            searchResponse.setCount(111);
            searchResponse.setData(mainSearch(query));
            searchResponse.setResult(true);
            response = searchResponse;
        } else {
            SearchErrorResponse errorResponse = new SearchErrorResponse("Задан пустой поисковый запрос");
            errorResponse.setResult(false);
            response = errorResponse;
        }
        return searchResponse;
    }

    private Set<Integer> findPagesByLemmas(List<String> sortedLemmas) {
        if (sortedLemmas.isEmpty()) return Collections.emptySet();

        Set<Integer> currentPages = null;

        for (String lemma : sortedLemmas) {
            Set<Integer> pages = lemmaIndex.getOrDefault(lemma, Collections.emptySet());

            if (currentPages == null) {
                currentPages = new HashSet<>(pages);
            } else {
                currentPages = currentPages.stream()
                        .filter(pages::contains)
                        .collect(Collectors.toSet());
            }

            if (currentPages.isEmpty()) break;
        }

        return currentPages != null ? currentPages : Collections.emptySet();
    }

    private Set<Integer> findPages(Map<String, Integer> sortedLemmas) {
        Set<Integer> resultPages = new HashSet<>();
        for (String lemma : sortedLemmas.keySet()) {
            List<IndexEntity> entries = indexRepository.findByLemmaId(sortedLemmas.get(lemma));
            if (resultPages.isEmpty()) {
                resultPages = entries.stream()
                        .map(indexEntry -> indexEntry.getPage().getId())
                        .collect(Collectors.toSet());
            } else {
                Set<Integer> currentPages = entries.stream()
                        .map(indexEntry -> indexEntry.getPage().getId())
                        .collect(Collectors.toSet());
                resultPages.retainAll(currentPages);
            }
            if (resultPages.isEmpty()) break;
        }
        return resultPages;
    }

    private List<SearchResult> mainSearch(String query) {
        LemmaFrequencyAnalyzer frequencyAnalyzer = new LemmaFrequencyAnalyzer();
        Map<String, Integer> excludedLemmas = excludeLemmas(frequencyAnalyzer.frequencyMap(query));
        Map<String, Integer> sortedLemmas = sortLemmas(excludedLemmas);
        Set<Integer> resultPages = findPages(sortedLemmas);
        //System.out.println(resultPages);
        //System.out.println(sortedLemmas.entrySet());

        List<SearchResult> results = calculateRelevance(resultPages, sortedLemmas);
        System.out.println(results);
        return results;
    }

    private List<SearchResult> calculateRelevance(Set<Integer> pageIds, Map<String, Integer> sortedLemmas) {
        Map<Integer, Float> relevanceMap = new HashMap<>();
        float maxRelevance = 0;

        for (Integer pageId : pageIds) {
            float relevance = 0;
            for (String lemma : sortedLemmas.keySet()) {
                relevance += indexRepository.findByLemmaId(sortedLemmas.get(lemma)).stream()
                        .filter(entry -> entry.getPage().getId().equals(pageId))
                        .map(IndexEntity::getRank)
                        .reduce(0.0f, Float::sum);
            }
            relevanceMap.put(pageId, relevance);
            maxRelevance = Math.max(maxRelevance, relevance);
        }

        float finalMaxRelevance = maxRelevance;
        return pageIds.stream()
                .map(pageId -> {
                    PageEntity page = pageRepository.findById(pageId).orElseThrow();
                    float relevance = relevanceMap.get(pageId) / finalMaxRelevance;
                    String snippet = generateSnippet(page.getContent(), sortedLemmas.keySet());
                    return new SearchResult(
                            page.getPath(),
                            extractTitleFromContent(page.getContent()),
                            snippet,
                            relevance
                    );
                })
                .toList();
    }

    private String extractTitleFromContent(String content) {
        Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : "No Title";
    }

    private String generateSnippet(String content, Set<String> lemmas) {
        String snippet = content.substring(0, Math.min(content.length(), 150));
        for (String lemma : lemmas) {
            snippet = snippet.replaceAll("(?i)" + lemma, "<b>$0</b>");
        }
        return snippet;
    }

    private Map<String, Integer> excludeLemmas(Map<String, Integer> queryLemmas) {
        List<LemmaEntity> siteLemmasEntities = lemmaRepository.findAllBySiteId(1);
        int pageCount = pageRepository.findAllBySiteId(1).size();
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
