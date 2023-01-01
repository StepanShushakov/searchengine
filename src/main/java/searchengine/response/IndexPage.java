package searchengine.response;

import java.net.MalformedURLException;
import java.net.URL;

public class IndexPage {
    String url;

    public URL getUrl() throws MalformedURLException {
        return new URL(url);
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
