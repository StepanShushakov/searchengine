package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.searching.SearchingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.response.IndexPage;
import searchengine.response.SearchRequest;
import searchengine.services.IndexingService;
import searchengine.services.SearchingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    private final IndexingService indexingService;
    private final SearchingService searchingService;
    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         SearchingService searchingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchingService = searchingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @GetMapping("/echo")
    public ResponseEntity<Boolean> echo() {
        return ResponseEntity.ok(indexingService.echo());
    }

    @GetMapping("/getPoolSize")
    public ResponseEntity<Integer> getPoolSize() {
        return ResponseEntity.ok(indexingService.getPoolSize());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexingResponse> indexPage(IndexPage indexPage){
        return ResponseEntity.ok(indexingService.indexPage(indexPage));
    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<SearchingResponse> search(@RequestParam String query,
                                                                  @RequestParam (required = false) String site,
                                                                  @RequestParam (defaultValue = "0") int offset,
                                                                  @RequestParam (defaultValue = "20") int limit) {
        SearchingResponse response = searchingService.search(new SearchRequest(query, site, offset, limit));
        if (response.isResult()) return ResponseEntity.ok(response);
        else return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
    }
}
