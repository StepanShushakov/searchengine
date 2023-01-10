package searchengine.records;

import searchengine.model.Portal;

public record PageDescription(String url, Portal portal, boolean isParent) {
}
