package searchengine.dto.indexing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    private static Logger logger4j = LogManager.getRootLogger();

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
        logger4j.info("join parent Thread: " + Thread.currentThread().getName() +
                " url: " + url + " parent: " + isParent);
        parentTask.join();
        pool.shutdown();
    }

    public static ForkJoinPool getPool() {
        return pool;
    }
}
