package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.internal.expression.function.AggregationFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.LemmaFrequencyAnalyzer;
import searchengine.dto.Response;
import searchengine.dto.search.SearchErrorResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.model.LemmaEntity;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService implements SearchRepository {

    private final int MAX_ALLOWED_PAGES = 20;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final PageRepository pageRepository;

    private final Map<String, Set<Integer>> lemmaIndex;

    public Response startSearch(String query) {
        Response response;
        boolean searchResult = splitQuery(query);
        if (searchResult) {
            SearchResponse searchResponse = new SearchResponse();
            searchResponse.setResult(true);
            response = searchResponse;
        } else {
            SearchErrorResponse errorResponse = new SearchErrorResponse("Фашики");
            errorResponse.setResult(false);
            response = errorResponse;
        }
        return response;
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

    private boolean splitQuery(String query) {
        LemmaFrequencyAnalyzer frequencyAnalyzer = new LemmaFrequencyAnalyzer();
        //Map<String, Integer> queryLemmas = frequencyAnalyzer.frequencyMap(query);
        Map<String, Integer> excludedLemmas = excludeLemmas(frequencyAnalyzer.frequencyMap(query));
        Map<String, Integer> sortedLemmas = sortLemmas(excludedLemmas);
        System.out.println(sortedLemmas.entrySet());
        return true;
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
        //lemmas.entrySet().removeIf(entry -> entry.getValue() > MAX_ALLOWED_PAGES);
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
