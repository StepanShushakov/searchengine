package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @PostMapping("/search")
    public ResponseEntity<SearchingResponse> search(SearchRequest searchRequest) {
        return ResponseEntity.ok(searchingService.search(searchRequest));
    }
}
