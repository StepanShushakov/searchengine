package searchengine.dto.searching;

import lombok.Data;

@Data
public class SearchingResponse {
    private boolean result;
    private int count;
    private SearchingData data;
    private String error;
}
