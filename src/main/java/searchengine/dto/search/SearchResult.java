package searchengine.dto.search;

public record SearchResult(
        String uri,
        String title,
        String snippet,
        double relevance
) {}
