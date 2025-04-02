package searchengine.dto.search;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import searchengine.dto.Response;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Setter
@Getter
public class SearchResponse extends Response {
    private int count;
    private List<SearchResult> data;
}
