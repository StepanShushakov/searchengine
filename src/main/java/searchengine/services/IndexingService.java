package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Page;
import searchengine.response.IndexPage;

public interface IndexingService {
    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    Boolean echo();

    Integer getPoolSize();

    IndexingResponse indexPage(IndexPage indexPage);
}
