package searchengine.dto.indexing;

import lombok.*;
import searchengine.dto.Response;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Setter
@Getter
public class ErrorResponse extends Response {
    private final String error;
}
