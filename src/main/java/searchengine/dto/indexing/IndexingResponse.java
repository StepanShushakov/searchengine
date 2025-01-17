package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingResponse {
    private boolean result;
    private String error;

    public IndexingResponse() {
        this.result = true;
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
