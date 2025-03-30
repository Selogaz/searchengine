package searchengine.dto.search;

public record SearchResult(
        String site,
        String siteName,
        String uri,
        String title,
        String snippet,
        float relevance
) {}
