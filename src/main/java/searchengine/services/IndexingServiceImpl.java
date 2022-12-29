package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.*;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.PageRepository;
import searchengine.repositories.PortalRepository;
import searchengine.response.IndexPage;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;

    @Autowired
    private final PortalRepository portalRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Value("${jsoupFakePerformance.userAgent}")
    private String userAgent;
    @Value("${jsoupFakePerformance.referrer}")
    private String referrer;
    @Value("${spring.datasource.username}")
    private String DBUserName;
    @Value("${spring.datasource.password}")
    private String DBPassword;
    @Value("${spring.datasource.url}")
    private String DBUrl;
    private Connection connection;
    private Statement statement;

    @Override
    public IndexingResponse startIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (getPoolSize() > 0) return responseError(response, "Индексация уже запущена");
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (Site site : sites.getSites()) {
            Portal portal = portalRepository.findByNameAndUrl(site.getName(), site.getUrl());
            if (portal != null) {
                deletePagesByPortal(portal);
                portalRepository.delete(portal);
            }
            Portal newPortal = new Portal();
            newPortal.setName(site.getName());
            String portalLink = site.getUrl();
            newPortal.setUrl(portalLink);
            newPortal.setStatus(IndexStatus.INDEXING);
            newPortal.setStatusTime(new Date());
            portalRepository.save(newPortal);
            try {
                executor.submit(new CrawlStarter(newPortal.getUrl()
                        , new URL(portalLink).getHost().replaceAll("^www.", "")
                        , newPortal
                        , new RepositoriesFactory(portalRepository, pageRepository)
                        , new ConnectionPerformance(userAgent, referrer)
                        , true));
            } catch (MalformedURLException e) {
                newPortal.setStatusTime(new Date());
                newPortal.setStatus(IndexStatus.FAILED);
                portalRepository.save(newPortal);
                return responseError(response, e.toString());
            }
        }
        closeStatementConnection();
        executor.shutdown();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        ForkJoinPool pool = CrawlStarter.getPool();
        if (pool.getPoolSize() == 0)
            return responseError(response, "Индексация не запущена");
        else {
            SiteLinker.setStopCrawling(true);
            pool.shutdown();
            try {
                if ((!pool.awaitTermination(800, TimeUnit.MILLISECONDS))) pool.shutdownNow();
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }

            while (pool.getPoolSize() > 0);  //подождем, пока завершатся задачи пула потоков
            List<Portal> portals = portalRepository.findAll();
            portals.forEach(portal -> {
                portal.setStatus(IndexStatus.FAILED);
                portal.setLastError("Индексация остановлена пользователем");
                portal.setStatusTime(new Date());
                portalRepository.save(portal);
            });
            response.setResult(pool.isShutdown());
            return response;
        }
    }

    public IndexingResponse indexPage(IndexPage indexPage){
        IndexingResponse response = new IndexingResponse();
        return response;
    }

    @Override
    public Boolean echo() {
        return true;
    }

    @Override
    public Integer getPoolSize() {
        return CrawlStarter.getPoolSize();
    }

    private IndexingResponse responseError(IndexingResponse response, String errorString) {
        response.setError(errorString);
        response.setResult(false);
        return response;
    }

    private void closeStatementConnection() {
        try {
            if (this.statement != null) this.statement.close();
            if (this.connection != null) this.connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void deletePagesByPortal(Portal portal) {
        try {
            if (this.connection == null ||
                    this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            this.statement.execute("delete from page where site_id = " + portal.getId());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
