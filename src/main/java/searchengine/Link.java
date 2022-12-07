package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
    private PortalRepository portalRepository;
    private PageRepository pageRepository;
    private ArrayList<String> childrenLinks = new ArrayList<>();

    private static int rnd(int min, int max){
        max -= min;
        return (int) (Math.random() * ++max) + min;
    }

    public Link(String url,
                String host,
                Page page,
                Portal portal,
                PortalRepository portalRepository,
                PageRepository pageRepository) {
        try {
            Thread.sleep(rnd(500, 5000));
        } catch (InterruptedException e) {
            //throw new RuntimeException(e);

        }
        this.url = url;
        this.host = host;
        this.portalRepository = portalRepository;
        this.pageRepository = pageRepository;
        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .get();
            page.setCode(doc.connection().response().statusCode());
        } catch (IOException e) {
            //throw new RuntimeException(e);
        }
        if (doc == null) {
            savePage(page, portal);
            return;
        }
        page.setContent(doc.toString());
        String pagePath = "";
        try {
            pagePath = new URL(url).getPath();
        } catch (MalformedURLException e) {
            //throw new RuntimeException(e);
        }
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
        this.pageRepository.save(page);
        portal.setStatusTime(new Date());
        this.portalRepository.save(portal);
    }
}