package searchengine.dto.indexing;

import lombok.Data;
import searchengine.config.SitesList;

@Data
public class IndexingResponse {
    private boolean result;
    private String error;

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
