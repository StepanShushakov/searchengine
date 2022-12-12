package searchengine;

import searchengine.model.Portal;

public class PortalDescription {

    private Portal portal;
    private String name;
    private String url;

    public PortalDescription(Portal portal, String name, String url) {
        this.portal = portal;
        this.name = name;
        this.url = url;
    }

    public Portal getPortal() {
        return portal;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}
