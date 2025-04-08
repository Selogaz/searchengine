package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.Response;
import searchengine.dto.search.SearchErrorResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.repository.StatisticsService;


import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    private final String DEFAULT_ERROR_MESSAGE = "Указанная страница не найдена";

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

    @GetMapping("/search")
    public ResponseEntity<Response> search(@RequestParam String query, @RequestParam(required = false) String site) {
        logger.info("Запуск поиска...");
        return ResponseEntity.ok(searchService.startSearch(query, site));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response> handleBadRequest() {
        return ResponseEntity.badRequest()
                .body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Response> handleNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Response> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleInternalError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
    }
}
