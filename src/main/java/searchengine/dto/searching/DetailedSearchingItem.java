package searchengine.dto.searching;

import lombok.Data;

@Data
public class DetailedSearchingItem {
    private String name;
    private String siteName;
    private String title;
    private String snippet;
    private float relevance;
}
