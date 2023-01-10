package searchengine.dto.indexing;

import com.google.common.util.concurrent.Uninterruptibles;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import searchengine.model.IndexEntity;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Portal;
import searchengine.records.ConnectionPerformance;
import searchengine.records.IndexingParameters;
import searchengine.records.PageDescription;
import searchengine.records.RepositoriesFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Link {
    private PageDescription pageDescription;
    private static RepositoriesFactory repositories;
    private static ConnectionPerformance connectionPerformance;
    private static LemmaFinder lemmaInstance;

    static {
        try {
            lemmaInstance = LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final ArrayList<String> childrenLinks = new ArrayList<>();


    public static void setRepositories(RepositoriesFactory repositories) {
        if (Link.repositories == null) Link.repositories = repositories;
    }

    public static void setConnectionPerformance(ConnectionPerformance connectionPerformance) {
        if (Link.connectionPerformance == null) Link.connectionPerformance = connectionPerformance;
    }

    private static int rnd(int min, int max){
        max -= min;
        return (int) (Math.random() * ++max) + min;
    }

    public Link(PageDescription pageDescription,
                RepositoriesFactory inputRepositories,
                ConnectionPerformance inputConnectionPerformance) {
        if (repositories == null) repositories = inputRepositories;
        String pagePath;
        pagePath = getPathByStringUrl(pageDescription.url());
//        if (linkIsAdded(pageDescription.portal(), pagePath)) return;
        Page page = new Page();
        Portal portal = pageDescription.portal();
        page.setPortal(portal);
        page.setPath(pagePath.isEmpty() ? "/" : pagePath);
        Uninterruptibles.sleepUninterruptibly(200/*rnd(500, 5000)*/, TimeUnit.MILLISECONDS);
        this.pageDescription = pageDescription;
        if (connectionPerformance == null) connectionPerformance = inputConnectionPerformance;
        Document doc = getDoc(page, portal);
        if (doc == null) return;
        page.setContent(doc.toString());
        savePage(page);
        if (pageDescription.isParent()) new Thread(() -> indexPage(page, doc, true)).start();
        else indexPage(page, doc, true);
        setChildrenLinks(doc);
    }

    public static void indexPage(Page page, Document doc, boolean isNew) {
        if (doc == null) return;
        try {
            indexTag(page,
                    doc.title(),
                    new IndexingParameters(1, isNew));
            indexTag(page,
                    doc.body().text(),
                    new IndexingParameters(0.8F, isNew));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Document getDoc(Page page, Portal portal) {
        Document doc = null;
        try {
            doc = Jsoup.connect(portal.getUrl() + page.getPath())
                    .userAgent(connectionPerformance.userAgent())
                    .referrer(connectionPerformance.referrer())
                    .get();
            page.setCode(doc.connection().response().statusCode());
        } catch (HttpStatusException e) {
            saveWithStatusCode(e.getStatusCode(), e.toString(), page);
        } catch (Exception e) {
            saveWithStatusCode(0, e.toString(), page);
        }
        return doc;
    }

    private String getPathByStringUrl(String url) {
        try {
            return new URL(url).getPath();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveWithStatusCode(int statusCode, String error, Page page) {
        page.setCode(statusCode);
        page.setContent(error);
        page.getPortal().setLastError(error);
        savePage(page);
    }

    private void setChildrenLinks(Document doc) {
        Elements elements = doc.select("a");
        if (elements.size() > 0) {
            elements.forEach(element -> {
                String childrenLink = element.attr("abs:href");
                if (childrenLink.toLowerCase().startsWith("http")
                        && linkIsCorrect(childrenLink.toLowerCase())) {
                    this.childrenLinks.add(childrenLink);
                }
            });
        }
    }

    private boolean linkIsCorrect(String childrenLink) {
        boolean hostIsCorrect;
        try {
            URL url2Verify = new URL(childrenLink);
            hostIsCorrect = (getPortalMainUrl(url2Verify))
                    .equals(this.pageDescription.portal().getUrl());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return hostIsCorrect
                && !childrenLink.endsWith(".doc")
                && !childrenLink.endsWith(".jpg")
                && !childrenLink.endsWith(".png")
                && !childrenLink.endsWith(".jpeg")
                && !childrenLink.endsWith(".bmp")
                && !childrenLink.endsWith(".txt")
                && !childrenLink.endsWith(".pdf")
                && !childrenLink.endsWith(".xls")
                && !childrenLink.endsWith(".docx")
                && !childrenLink.endsWith(".gif")
                && !childrenLink.endsWith(".webp")
                ;
    }

    public ArrayList<String> getChildrenLinks() {
        return childrenLinks;
    }

    private static void savePage(Page page) {
        Portal portal = page.getPortal();
//        if (linkIsAdded(portal, page.getPath())) return;
        repositories.pageRepository().save(page);
        savePortal(portal);
    }

    private static void savePortal(Portal portal) {
        portal.setStatusTime(new Date());
        repositories.portalRepository().save(portal);
    }

    private static boolean linkIsAdded(Portal portal, String path) {
        return repositories.pageRepository().findByPortalAndPath(portal, path).size() != 0;
    }

    public static void indexTag(Page page, String text, IndexingParameters parameters) throws IOException {
        Map<String, Integer > lemmas = lemmaInstance.collectLemmas(text);
        lemmas.forEach((lemma, rank) -> {
           List<Lemma> lemmasList = repositories.lemmaRepository().findByPortalAndLemma(page.getPortal(), lemma);
           Lemma lemmaRecord;
            if (lemmasList.size() == 0) {
               lemmaRecord = new Lemma();
               lemmaRecord.setPortal(page.getPortal());
           } else {
               lemmaRecord = lemmasList.get(0);
           }
            lemmaRecord.setLemma(lemma);

           if (parameters.isNew()) lemmaRecord
                   .setFrequency(lemmaRecord.getFrequency() + 1);   //если лемма встетится как в
                                                                    //head так и в body посчитаем её
                                                                    //дважды, пока не будем думать,
                                                                    //что это ошибка, пусть вероятность
                                                                    //встречи леммы в разных тегах
                                                                    //увеличивает frequency страницы
           repositories.lemmaRepository().save(lemmaRecord);
           List<IndexEntity> indexes = repositories.indexRepository().findByPageAndLemma(page, lemmaRecord);
           IndexEntity index;
           if (indexes.size() != 0) index = indexes.get(0);
           else index = new IndexEntity();
           index.setPage(page);
           index.setLemma(lemmaRecord);
           index.setRank(rank * parameters.ratio());
           repositories.indexRepository().save(index);
        });
    }

    public static URL getUrlFromString(String string) throws MalformedURLException {
        return new URL(string);
    }

    public static String getPortalMainUrl(URL url) {
        return url.getProtocol() + "://" + url.getHost().replaceAll("^www.", "");
    }

    public static String getPath(String string) throws MalformedURLException {
        URL url = getUrlFromString(string);
        return url.getPath();
    }
}
