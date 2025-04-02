package searchengine.dto.search;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import searchengine.dto.Response;

@EqualsAndHashCode(callSuper = true)
@Setter
@Getter
@RequiredArgsConstructor
public class SearchErrorResponse extends Response {
    private final String error;
}
