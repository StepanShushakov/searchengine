package searchengine;

import searchengine.model.Portal;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.logging.Logger;

public class SiteLinker extends RecursiveAction {
    private PageDescription pageDescription;
    private Link link;
    private RepositoriesFactory repositories;
    private ConnectionPerformance connectionPerformance;

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
