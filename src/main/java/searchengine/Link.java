package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import searchengine.model.Page;
import searchengine.model.PageRepository;
import searchengine.model.Portal;
import searchengine.model.PortalRepository;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

public class Link {
    private String url;
    private String host;
    private RepositoriesFactory repositories;
    private ConnectionPerformance connectionPerformance;
    private ArrayList<String> childrenLinks = new ArrayList<>();

    private static int rnd(int min, int max){
        max -= min;
        return (int) (Math.random() * ++max) + min;
    }

    public Link(String url,
                String host,
                Page page,
                Portal portal,
                RepositoriesFactory repositories,
                ConnectionPerformance connectionPerformance) {
        this.repositories = repositories;
        String pagePath = "";
        try {
            pagePath = new URL(url).getPath();
        } catch (MalformedURLException e) {
            //throw new RuntimeException(e);
        }
        if (linkIsAdded(portal, pagePath)) return;
        try {
            Thread.sleep(rnd(500, 5000));
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
        }
        this.url = url;
        this.host = host;
        this.connectionPerformance = connectionPerformance;
        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(connectionPerformance.getUserAgent())
                    .referrer(connectionPerformance.getReferrer())
                    .get();
            page.setCode(doc.connection().response().statusCode());
        } catch (IOException e) {
            //throw new RuntimeException(e);
        }
        if (doc == null) {
            return;
        }
        page.setContent(doc.toString());
        page.setPath(pagePath.isEmpty() ? "/" : pagePath);
        savePage(page, portal);
        Elements elements = doc.select("a");
        if (elements.size() > 0) {
            elements.forEach(element -> {
                String childrenLink = element.attr("href");
                if (childrenLink.toLowerCase().startsWith("http")
                    && linkIsCorrect(childrenLink)) {
                    this.childrenLinks.add(childrenLink);
                }
            });
        }
    }

    private boolean linkIsCorrect(String childrenLink) {
        try {
            return new URL(childrenLink).getHost().replaceAll("^www.", "").equals(this.host);
        } catch (MalformedURLException e) {
            //throw new RuntimeException(e);
            return false;
        }
    }

    public ArrayList<String> getChildrenLinks() {
        return childrenLinks;
    }

    private void savePage(Page page, Portal portal) {
        if (repositories.getPageRepository().findByPortalAndPath(portal, page.getPath()).size() != 0) return;
        repositories.getPageRepository().save(page);
        portal.setStatusTime(new Date());
        repositories.getPortalRepository().save(portal);
    }

    private boolean linkIsAdded(Portal portal, String path) {
        return repositories.getPageRepository().findByPortalAndPath(portal, path).size() != 0;
    }
}
