package searchengine.services.supportingServices;

import searchengine.model.Portal;
import searchengine.records.ConnectionPerformance;
import searchengine.records.RepositoriesFactory;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class CrawlStarter implements Runnable{
    private static final ForkJoinPool pool = new ForkJoinPool();
    private final Portal portal;
    private static RepositoriesFactory repositories;
    private static ConnectionPerformance connectionPerformance;

    public CrawlStarter(Portal portal) {
        this.portal = portal;
        SiteLinker.setRepositories(CrawlStarter.repositories);
        SiteLinker.setConnectionPerformance(CrawlStarter.connectionPerformance);
    }

    public static void setRepositoriesFactory(RepositoriesFactory repositories) {
        if (CrawlStarter.repositories == null) CrawlStarter.repositories = repositories;
    }

    public static void setConnectionPerformance(ConnectionPerformance connectionPerformance) {
        if (CrawlStarter.connectionPerformance == null) CrawlStarter.connectionPerformance = connectionPerformance;
    }

    public static Integer getPoolSize() {
        return pool.getPoolSize();
    }

    @Override
    public void run() {
        ForkJoinTask<Void> parentTask = pool.submit(new SiteLinker(portal.getUrl(),
                                                                    portal,
                                                                    true));
        parentTask.join();
        pool.shutdown();
    }

    public static ForkJoinPool getPool() {
        return pool;
    }
}
