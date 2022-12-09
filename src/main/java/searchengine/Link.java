package searchengine;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Portal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

public class Link {
    private PageDescription pageDescription;
    private RepositoriesFactory repositories;
    private ConnectionPerformance connectionPerformance;
    private ArrayList<String> childrenLinks = new ArrayList<>();

    private static int rnd(int min, int max){
        max -= min;
        return (int) (Math.random() * ++max) + min;
    }

    public Link(PageDescription pageDescription,
                RepositoriesFactory repositories,
                ConnectionPerformance connectionPerformance) {
        this.repositories = repositories;
        String pagePath = "";
        try {
            pagePath = new URL(pageDescription.getUrl()).getPath();
        } catch (MalformedURLException e) {
            //throw new RuntimeException(e);
        }
        Page page = new Page();
        page.setPortal(pageDescription.getPortal());
        page.setPath(pagePath.isEmpty() ? "/" : pagePath);
        if (linkIsAdded(pageDescription.getPortal(), pagePath)) return;
        try {
            Thread.sleep(rnd(500, 5000));
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);
        }
        this.pageDescription = pageDescription;
        this.connectionPerformance = connectionPerformance;
        Document doc = null;
        try {
            doc = Jsoup.connect(pageDescription.getUrl())
                    .userAgent(connectionPerformance.getUserAgent())
                    .referrer(connectionPerformance.getReferrer())
                    .get();
            page.setCode(doc.connection().response().statusCode());
        } catch (IOException e) {
            page.setCode(((HttpStatusException) e).getStatusCode());
            page.setContent(e.toString());
            savePage(page);
        }
        if (doc == null) return;
        page.setContent(doc.toString());
        savePage(page);
        Elements elements = doc.select("a");
        if (elements.size() > 0) {
            elements.forEach(element -> {
                String childrenLink = element.attr("abs:href");
                if (childrenLink.toLowerCase().startsWith("http")
                    && linkIsCorrect(childrenLink)) {
                    this.childrenLinks.add(childrenLink);
                }
            });
        }
    }

    private boolean linkIsCorrect(String childrenLink) {
        try {
            return new URL(childrenLink).getHost().replaceAll("^www.", "")
                    .equals(this.pageDescription.getHost());
        } catch (MalformedURLException e) {
            //throw new RuntimeException(e);
            Logger.getLogger(Link.class.getName()).info("catch at url pulling: " + e);
            return false;
        }
    }

    public ArrayList<String> getChildrenLinks() {
        return childrenLinks;
    }

    private void savePage(Page page) {
        Portal portal = page.getPortal();
        if (linkIsAdded(portal, page.getPath())) return;
        repositories.getPageRepository().save(page);
        portal.setStatusTime(new Date());
        repositories.getPortalRepository().save(portal);
    }

    private boolean linkIsAdded(Portal portal, String path) {
        return repositories.getPageRepository().findByPortalAndPath(portal, path).size() != 0;
    }
}
