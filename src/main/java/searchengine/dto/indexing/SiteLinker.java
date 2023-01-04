package searchengine.dto.indexing;

import lombok.SneakyThrows;
import searchengine.model.IndexStatus;
import searchengine.model.Portal;
import searchengine.records.ConnectionPerformance;
import searchengine.records.PageDescription;
import searchengine.records.RepositoriesFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;

public class SiteLinker extends RecursiveAction {
    private final PageDescription pageDescription;
    private final Link link;
    private final RepositoriesFactory repositories;
    private final ConnectionPerformance connectionPerformance;
    private static Boolean stopCrawling = false;
    private final Boolean isParent;
    private static final HashSet<String> verifySet = new HashSet<>();

    public SiteLinker(String url,
                      String host,
                      Portal portal,
                      RepositoriesFactory repositories,
                      ConnectionPerformance connectionPerformance,
                      Boolean isParent) {
        this.repositories = repositories;
        this.connectionPerformance = connectionPerformance;
        this.pageDescription = new PageDescription(url, host, portal);
        this.link = new Link(pageDescription, repositories, connectionPerformance);
        this.isParent = isParent;
    }

    @SneakyThrows
    @Override
    protected void compute() {
        if (stopCrawling) return;
        Logger.getLogger(SiteLinker.class.getName())
                .info("Compute method. Thread: " + Thread.currentThread().getName()
                        + " url: " + this.pageDescription.url()
                        + " parent: " + this.isParent);
        List<SiteLinker> taskList = new ArrayList<>();
        List<String> childrenLinks = this.link.getChildrenLinks();
        for (String link : childrenLinks) {
            if (linkIsAdded(this.pageDescription.portal(), link)) continue;
            verifySet.add((pageDescription.portal() + Link.getPath(link)).toLowerCase());
            SiteLinker task = new SiteLinker(link
                    , this.pageDescription.host()
                    , this.pageDescription.portal()
                    , this.repositories
                    , this.connectionPerformance
                    , false);
            task.fork();
            taskList.add(task);
        }
        for (SiteLinker task : taskList) {
//            Logger.getLogger(SiteLinker.class.getName())
//                    .info("join Thread: " + Thread.currentThread().getName()
//                            + " url: "+ task.getPageDescription().url()
//                            + " parent: " + task.isParent());
            task.join();
        }
        if (this.isParent) {
            Portal portal = this.getPageDescription().portal();
            portal.setStatus(IndexStatus.INDEXED);
            portal.setStatusTime(new Date());
            this.getRepositories().portalRepository().save(portal);
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
}
