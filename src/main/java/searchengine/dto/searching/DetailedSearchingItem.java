package searchengine.dto.searching;

import lombok.Data;

@Data
public class DetailedSearchingItem {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
