package searchengine.records;

import searchengine.model.Portal;

public record PageDescription(String url, String host, Portal portal) {
}
