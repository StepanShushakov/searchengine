package searchengine.dto.indexing;

import searchengine.model.Portal;
import searchengine.records.ConnectionPerformance;
import searchengine.records.RepositoriesFactory;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class CrawlStarter implements Runnable{
    private static final ForkJoinPool pool = new ForkJoinPool();

    private final String url;
    private final String host;
    private final Portal portal;
    RepositoriesFactory repositoriesFactory;
    ConnectionPerformance connectionPerformance;
    private final boolean isParent;

    public CrawlStarter(String url,
                        String host,
                        Portal portal,
                        RepositoriesFactory repositoriesFactory,
                        ConnectionPerformance connectionPerformance,
                        boolean isParent) {
        this.url = url;
        this.host = host;
        this.portal = portal;
        this.repositoriesFactory = repositoriesFactory;
        this.connectionPerformance = connectionPerformance;
        this.isParent = isParent;
    }

    public static Integer getPoolSize() {
        return pool.getPoolSize();
    }

    @Override
    public void run() {
        ForkJoinTask<Void> parentTask = pool.submit(new SiteLinker(url,
                                                                    host,
                                                                    portal,
                                                                    repositoriesFactory,
                                                                    connectionPerformance,
                                                                    isParent));
        parentTask.join();
        pool.shutdown();
    }

    public static ForkJoinPool getPool() {
        return pool;
    }
}
