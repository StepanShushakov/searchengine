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
import java.util.logging.Logger;

public class Link {
    private PageDescription pageDescription;
    private final RepositoriesFactory repositories;
    private final ArrayList<String> childrenLinks = new ArrayList<>();

    private static int rnd(int min, int max){
        max -= min;
        return (int) (Math.random() * ++max) + min;
    }

    public Link(PageDescription pageDescription,
                RepositoriesFactory repositories,
                ConnectionPerformance connectionPerformance) {
        this.repositories = repositories;
        String pagePath;
        try {
            pagePath = new URL(pageDescription.url()).getPath();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        if (linkIsAdded(pageDescription.portal(), pagePath)) return;
        Page page = new Page();
        Portal portal = pageDescription.portal();
        page.setPortal(portal);
        page.setPath(pagePath.isEmpty() ? "/" : pagePath);
        Uninterruptibles.sleepUninterruptibly(rnd(500, 5000), TimeUnit.MILLISECONDS);
        this.pageDescription = pageDescription;
        Document doc = null;
        try {
            doc = Jsoup.connect(pageDescription.url())
                    .userAgent(connectionPerformance.userAgent())
                    .referrer(connectionPerformance.referrer())
                    .get();
            page.setCode(doc.connection().response().statusCode());
        } catch (HttpStatusException e) {
            page.setCode(e.getStatusCode());
            page.setContent(e.toString());
            portal.setLastError(e.toString());
            savePage(page);
        } catch (Exception e) {
            page.setCode(0);
            page.setContent(e.toString());
            portal.setLastError(e.toString());
            savePage(page);
        }
        if (doc == null) return;
        page.setContent(doc.toString());
        savePage(page);
        try {
//            Logger.getLogger(Link.class.getName()).info("индексируем страницу: " + pageDescription.url());
            indexPage(page, repositories, true);
        } catch (IOException e) {
            Logger.getLogger(Link.class.getName()).info("ошибка индексации страницы "
                                                            + pageDescription.url() + " : " + e);
        }
        setChildrenLinks(doc);
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
            hostIsCorrect = new URL(childrenLink).getHost().replaceAll("^www.", "")
                    .equals(this.pageDescription.host());
        } catch (MalformedURLException e) {
            //throw new RuntimeException(e);
            Logger.getLogger(Link.class.getName()).info("catch at url pulling: " + e);
            return false;
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
//                && !childrenLink.endsWith(".ps")
//                && !childrenLink.endsWith(".wn")
//                && !childrenLink.endsWith(".l")
//                && !childrenLink.endsWith(".h")
//                && !childrenLink.endsWith(".dtd")
//                && !childrenLink.endsWith(".pl")
//                && !childrenLink.endsWith(".c")
//                && !childrenLink.endsWith(".sed")
//                && !childrenLink.endsWith(".app")
//                && !childrenLink.endsWith(".draw")
//                && !childrenLink.endsWith(".tex")
//                && !childrenLink.endsWith(".edu")
//                && !childrenLink.endsWith(".jp")
                ;
    }

    public ArrayList<String> getChildrenLinks() {
        return childrenLinks;
    }

    private void savePage(Page page) {
        Portal portal = page.getPortal();
        if (linkIsAdded(portal, page.getPath())) return;
        repositories.pageRepository().save(page);
        savePortal(portal);
    }

    private void savePortal(Portal portal) {
        portal.setStatusTime(new Date());
        repositories.portalRepository().save(portal);
    }

    private boolean linkIsAdded(Portal portal, String path) {
        return repositories.pageRepository().findByPortalAndPath(portal, path).size() != 0;
    }

    public static void indexPage(Page page, RepositoriesFactory repositories, Boolean isNew) throws IOException {
        String text = page.getContent();
        Map<String, Integer /*ArrayList<String>*/> lemmas = LemmaFinder
                                        .getInstance()
                                        .collectLemmas(text.replaceAll("<[^>]+>", ""));
        lemmas.forEach((lemma, rank) -> {
//           Logger.getLogger(Link.class.getName()).info(page.getPath() + ": " + lemma + " " + words.size());
           List<Lemma> lemmasList = repositories.lemmaRepository().findByPortalAndLemma(page.getPortal(), lemma);
           Lemma lemmaRecord;
            /*String stringWords = words.toString().replace("[", "").replace("]", "");*/
            if (lemmasList.size() == 0) {
               lemmaRecord = new Lemma();
               lemmaRecord.setPortal(page.getPortal());
               /*lemmaRecord.setWord(stringWords);*/
           } else {
               lemmaRecord = lemmasList.get(0);
               /*lemmaRecord.setWord(lemmaRecord.getWord()
                       + ", "
                       + stringWords);*/
           }
            lemmaRecord.setLemma(lemma);

           if (isNew) lemmaRecord.setFrequency(lemmaRecord.getFrequency() + 1);
           repositories.lemmaRepository().save(lemmaRecord);
           List<IndexEntity> indexes = repositories.indexRepository().findByPageAndLemma(page, lemmaRecord);
           IndexEntity index;
           if (indexes.size() != 0) index = indexes.get(0);
           else index = new IndexEntity();
           index.setPage(page);
           index.setLemma(lemmaRecord);
           index.setRank(rank);
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
