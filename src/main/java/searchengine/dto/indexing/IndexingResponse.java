package searchengine.dto.indexing;

import lombok.Data;
import searchengine.config.SitesList;

@Data
public class IndexingResponse {
    private SitesList sites;

    public void setSites(SitesList sites) {
        this.sites = sites;
    }
}
