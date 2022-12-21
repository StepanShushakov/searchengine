package searchengine;

import org.springframework.stereotype.Component;
import searchengine.model.IndexStatus;
import searchengine.model.Portal;

import java.util.Date;
import java.util.concurrent.ForkJoinPool;

public class CrawlStarter implements Runnable{

    private String url;
    private String host;
    private Portal portal;
    RepositoriesFactory repositoriesFactory;
    ConnectionPerformance connectionPerformance;

    public CrawlStarter(String url,
                        String host,
                        Portal portal,
                        RepositoriesFactory repositoriesFactory,
                        ConnectionPerformance connectionPerformance) {
    this.url = url;
    this.host = host;
    this.portal = portal;
    this.repositoriesFactory = repositoriesFactory;
    this.connectionPerformance = connectionPerformance;
    }

    @Override
    public void run() {
        ForkJoinPool pool = new ForkJoinPool();
        pool.invoke(new SiteLinker(url, host, portal, repositoriesFactory, connectionPerformance));
        pool.shutdown();
        portal.setStatus(IndexStatus.INDEXED);
        portal.setStatusTime(new Date());
        repositoriesFactory.getPortalRepository().save(portal);
    }
}
