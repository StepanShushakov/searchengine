package searchengine;

import searchengine.model.IndexStatus;
import searchengine.model.Portal;
import searchengine.model.PortalRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;

public class SiteLinker extends RecursiveAction {
    private PageDescription pageDescription;
    private Link link;
    private RepositoriesFactory repositories;
    private ConnectionPerformance connectionPerformance;
    private static Boolean stopCrawling = false;

    public SiteLinker(String url,
                      String host,
                      Portal portal,
                      RepositoriesFactory repositories,
                      ConnectionPerformance connectionPerformance) {
        this.repositories = repositories;
        this.connectionPerformance = connectionPerformance;
        this.pageDescription = new PageDescription(url, host, portal);
        this.link = new Link(pageDescription, repositories, connectionPerformance);
    }

    @Override
    protected void compute() {
        if (stopCrawling) return;
        Logger.getLogger(SiteLinker.class.getName())
                .info("Compute method. Thread: " + Thread.currentThread().getName()
                        + " url: " + this.pageDescription.getUrl());
        List<SiteLinker> taskList = new ArrayList<>();
        List<String> childrenLinks = this.link.getChildrenLinks();
        for (int i = 0; i < childrenLinks.size(); i++) {
            String link = childrenLinks.get(i);
            if (linkIsAdded(link)) continue;
            SiteLinker task = new SiteLinker(link
                    ,this.pageDescription.getHost()
                    ,this.pageDescription.getPortal()
                    ,this.repositories
                    ,this.connectionPerformance);
            task.fork();
            taskList.add(task);
        }
        for (SiteLinker task : taskList) {
            Logger.getLogger(SiteLinker.class.getName())
                    .info("join Thread: " + Thread.currentThread().getName());
            task.join();
        }
//        PortalRepository portalRepository = repositories.getPortalRepository();
//        List<Portal> portals = portalRepository.findAll();
//        portals.forEach(portal -> {
//            portal.setStatus(IndexStatus.INDEXED);
//            portal.setStatusTime(new Date());
//            portalRepository.save(portal);
//        });
    }

    public static void setStopCrawling(Boolean stopCrawling) {
        SiteLinker.stopCrawling = stopCrawling;
    }

    private boolean linkIsAdded(String link) {
        String path = "";
        try {
            path = new URL(link).getPath();
            if (path.isEmpty()) path = "/";
        } catch (MalformedURLException e) {
            return true;    //если произойдёт исключение, скажем,
            // что страница уже добавлена,
            // что бы не пытаться её разбирать
        }
        return repositories.getPageRepository().findByPortalAndPath(this.pageDescription.getPortal(), path).size() != 0;
    }
}
