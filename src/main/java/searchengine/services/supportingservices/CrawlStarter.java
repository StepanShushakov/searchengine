package searchengine.services.supportingservices;

import searchengine.model.Portal;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

public class CrawlStarter implements Runnable{
    private static ForkJoinPool pool = new ForkJoinPool();
    private final Portal portal;

    public CrawlStarter(Portal portal) {
        if (CrawlStarter.pool.isShutdown() && SiteLinker.indexingStarted()) {
            CrawlStarter.pool = new ForkJoinPool();
        }
        this.portal = portal;
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
        //pool.shutdown();
    }

    public static ForkJoinPool getPool() {
        return pool;
    }
}
