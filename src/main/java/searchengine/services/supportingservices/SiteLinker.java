package searchengine.services.supportingservices;

import lombok.SneakyThrows;
import searchengine.model.IndexStatus;
import searchengine.model.Portal;
import searchengine.records.ConnectionPerformance;
import searchengine.records.PageDescription;
import searchengine.records.RepositoriesFactory;
import searchengine.repositories.PortalRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

public class SiteLinker extends RecursiveAction {
    private final Link link;
    private final PageDescription pageDescription;
    private static RepositoriesFactory repositories;
    private static ConnectionPerformance connectionPerformance;
    private static Boolean stopCrawling = false;
    private static Set<String> verifySet;
    private static boolean indexingStarted = false;

    public SiteLinker(String url, Portal portal, boolean isNew) {
        this.pageDescription = new PageDescription(url, portal, isNew);
        this.link = new Link(pageDescription,
                                SiteLinker.repositories,
                                SiteLinker.connectionPerformance);
    }

    public static void setRepositories(RepositoriesFactory repositories) {
        if (SiteLinker.repositories == null) SiteLinker.repositories = repositories;
    }

    public static void setConnectionPerformance(ConnectionPerformance connectionPerformance) {
        if (SiteLinker.connectionPerformance ==null) SiteLinker.connectionPerformance = connectionPerformance;
    }

    @SneakyThrows
    @Override
    protected void compute() {
        if (stopCrawling) return;
        List<SiteLinker> taskList = new ArrayList<>();
        List<String> childrenLinks = this.link.getChildrenLinks();
        for (String link : childrenLinks) {
            if (linkIsAdded(this.pageDescription.portal(), link)) continue;
            verifySet.add((pageDescription.portal() + Link.getPath(link)).toLowerCase());
            SiteLinker task = new SiteLinker(link, pageDescription.portal(), false);
            task.fork();
            taskList.add(task);
        }
        for (SiteLinker task : taskList) {
            task.join();
        }
        if (this.pageDescription.isParent()) {
            Portal portal = this.getPageDescription().portal();
            portal.setStatus(portal.isErrorMainPage() ? IndexStatus.FAILED : IndexStatus.INDEXED);
            portal.setStatusTime(new Date());
            PortalRepository portalRepository = this.getRepositories().portalRepository();
            portalRepository.save(portal);
            if (portalRepository.countByStatus(IndexStatus.INDEXING) == 0) {
                SiteLinker.setIndexingStarted(false);
            }
        }
    }

    public PageDescription getPageDescription() {
        return pageDescription;
    }

    public RepositoriesFactory getRepositories() {
        return repositories;
    }

    public static void setStopCrawling(Boolean stopCrawling) {
        SiteLinker.stopCrawling = stopCrawling;
    }

    @SneakyThrows
    public static boolean linkIsAdded(Portal portal, String link) {
        return verifySet.contains((portal + Link.getPath(link)).toLowerCase());
    }

    public static void setIndexingStarted(boolean indexingStarted) {
        SiteLinker.indexingStarted = indexingStarted;
        if (indexingStarted) setStopCrawling(false);
    }

    public static boolean indexingStarted() {
        return indexingStarted;
    }

    public static void initVerifySet() {
        SiteLinker.verifySet = ConcurrentHashMap.newKeySet();
    }
}
