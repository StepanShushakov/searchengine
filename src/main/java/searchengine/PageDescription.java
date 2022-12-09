package searchengine;

import searchengine.model.Page;
import searchengine.model.Portal;

public class PageDescription {
    private String url;
    private String host;
    private Portal portal;

    public PageDescription(String url, String host, Portal portal) {
        this.url = url;
        this.host = host;
        this.portal = portal;
    }

    public String getUrl() {
        return url;
    }

    public String getHost() {
        return host;
    }

    public Portal getPortal() {
        return portal;
    }
}
