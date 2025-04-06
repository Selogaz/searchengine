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
import searchengine.services.StatisticsService;


import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

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
        return errorHandler(indexingService.startFullIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        return errorHandler(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<?> handleIndexPage(@RequestParam String url) {
        return errorHandler(indexingService.addOrUpdatePage(url));
    }

    @GetMapping("/search")
    public ResponseEntity<Response> search(@RequestParam String query, @RequestParam(required = false) String site) {
        logger.info("Запуск поиска...");
        return errorHandler(searchService.startSearch(query, site));
    }

    private ResponseEntity<Response> errorHandler(Response response) {
        final String DEFAULT_ERROR_MESSAGE = "Указанная страница не найдена";

        try {
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            //400
            logger.error("Bad Request", ex);
            return ResponseEntity.badRequest().body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
        } catch (SecurityException ex) {
            // 401
            logger.error("Unauthorized", ex);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
        } catch (NoSuchElementException ex) {
            // 404
            logger.error("Page not found", ex);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
        } catch (Exception ex) {
            // 500
            logger.error("Internal server error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new SearchErrorResponse(DEFAULT_ERROR_MESSAGE));
        }
    }
}
