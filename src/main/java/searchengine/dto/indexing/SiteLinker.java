package searchengine.dto.indexing;

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
    private final PageDescription pageDescription;
    private final Link link;
    private final RepositoriesFactory repositories;
    private final ConnectionPerformance connectionPerformance;
    private static Boolean stopCrawling = false;
    private final Boolean isParent;
    private static final Set<String> verifySet = ConcurrentHashMap.newKeySet();
    private static boolean indexingStarted = false;

    public SiteLinker(String url,
                      Portal portal,
                      RepositoriesFactory repositories,
                      ConnectionPerformance connectionPerformance,
                      Boolean isParent) {
        this.repositories = repositories;
        this.connectionPerformance = connectionPerformance;
        this.pageDescription = new PageDescription(url, portal, isParent);
        this.link = new Link(pageDescription, repositories, connectionPerformance);
        this.isParent = isParent;
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
            SiteLinker task = new SiteLinker(link
                    , this.pageDescription.portal()
                    , this.repositories
                    , this.connectionPerformance
                    , false);
            task.fork();
            taskList.add(task);
        }
        for (SiteLinker task : taskList) {
            task.join();
        }
        if (this.isParent) {
            Portal portal = this.getPageDescription().portal();
            portal.setStatus(IndexStatus.INDEXED);
            portal.setStatusTime(new Date());
            PortalRepository portalRepository = this.getRepositories().portalRepository();
            portalRepository.save(portal);
            if (portalRepository.countByStatusNot(IndexStatus.INDEXED) == 0) {
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

    public Boolean isParent() {
        return isParent;
    }

    @SneakyThrows
    public static boolean linkIsAdded(Portal portal, String link) {
        return verifySet.contains((portal + Link.getPath(link)).toLowerCase());
    }

    public static void setIndexingStarted(boolean indexingStarted) {
        SiteLinker.indexingStarted = indexingStarted;
    }

    public static boolean indexingStarted() {
        return indexingStarted;
    }
}
