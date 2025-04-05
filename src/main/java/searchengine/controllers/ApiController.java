package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.ApiError;
import searchengine.dto.Response;
import searchengine.dto.indexing.ErrorResponse;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchErrorResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.nio.file.AccessDeniedException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        return ResponseEntity.ok(indexingService.startFullIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> handleIndexPage(@RequestParam String url) {
        return ResponseEntity.ok(indexingService.addOrUpdatePage(url));
    }

//    @GetMapping("/search")
//    public ResponseEntity<ApiError> search(@RequestParam String query, @RequestParam(required = false) String site) {
//        try {
//            Object searchResult = searchService.startSearch(query, site);
//            return ResponseEntity.ok(new ApiError(true));
//        } catch (IllegalArgumentException ex) {
//            throw new IllegalArgumentException("Некорректные параметры запроса: " + ex.getMessage());
//        } catch (ResourceNotFoundException ex) {
//            throw new ResourceNotFoundException("Указанная страница не найдена");
//        } catch (Exception ex) {
//            throw new RuntimeException("Произошла ошибка при выполнении поиска", ex);
//        }
//    }
    @GetMapping("/search")
    public ResponseEntity<Response> search(@RequestParam String query, @RequestParam String site) {
        try {
            return ResponseEntity.ok(searchService.startSearch(query, site));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new SearchErrorResponse("Указанная страница не найдена"));
        } catch (SecurityException ex) {
            // Ошибка авторизации (401)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new SearchErrorResponse(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            // Ресурс не найден (404)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new SearchErrorResponse(ex.getMessage()));
        } catch (Exception ex) {
            // Внутренняя ошибка сервера (500)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new SearchErrorResponse("Произошла внутренняя ошибка сервера"));
        }
    }

    private ResponseEntity<Response> createErrorResponse(String errorMessage, int statusCode) {
        ErrorResponse errorResponse = new ErrorResponse(errorMessage);
        errorResponse.setResult(false);
        return ResponseEntity.status(statusCode).body(errorResponse);
    }
}
