package searchengine.services;

import searchengine.dto.searching.SearchingResponse;
import searchengine.response.SearchRequest;

public interface SearchingService {
    SearchingResponse search(SearchRequest searchRequest);
}
