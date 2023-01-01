package searchengine.config;

import lombok.Getter;
import lombok.Setter;

import java.net.MalformedURLException;
import java.net.URL;

@Setter
@Getter
public class Site {
    private String url;
    private String name;

    public String getUrl() {
        URL portalUrl = null;
        try {
            portalUrl = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return portalUrl.getProtocol() + "://" + portalUrl.getHost().replaceAll("^www.", "");
    }

}
