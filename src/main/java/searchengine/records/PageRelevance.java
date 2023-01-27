package searchengine.records;

import searchengine.model.Page;

public record PageRelevance(Page page, float relevance, int totalCount) {
}
