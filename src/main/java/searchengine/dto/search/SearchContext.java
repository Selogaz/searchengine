package searchengine.dto.search;

import java.util.Map;

public class SearchContext {
    private final Map<String, Integer> sortedLemmas;
    private final float maxRelevance;

    public SearchContext(Map<String, Integer> sortedLemmas, float maxRelevance) {
        this.sortedLemmas = sortedLemmas;
        this.maxRelevance = maxRelevance;
    }

    public Map<String, Integer> getSortedLemmas() {
        return sortedLemmas;
    }

    public float getMaxRelevance() {
        return maxRelevance;
    }
}
