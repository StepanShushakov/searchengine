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

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;

    @Autowired
    private PortalRepository portalRepository;
    @Autowired
    private PageRepository pageRepository;
    private ForkJoinPool pool = new ForkJoinPool();
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
        ArrayList<PortalDescription> portals = new ArrayList<>();
        for (Site site: sites.getSites()) {
            Portal portal = portalRepository.findByNameAndUrl(site.getName(), site.getUrl());
            if (portal != null && portal.getStatus() == IndexStatus.INDEXING)
                return responseError(response, "Индексация уже запущена");
            else portals.add(new PortalDescription(portal, site.getName(), site.getUrl()));
        }
        for (PortalDescription portalDescription : portals) {
            Portal portal = portalDescription.getPortal();
            if (portal != null) {
                deletePagesByPortal(portal);
                portalRepository.delete(portal);
            }
            Portal newPortal = new Portal();
            newPortal.setName(portalDescription.getName());
            String portalLink = portalDescription.getUrl();
            newPortal.setUrl(portalLink);
            newPortal.setStatus(IndexStatus.INDEXING);
            newPortal.setStatusTime(new Date());
            portalRepository.save(newPortal);
            try {
                if (this.pool.isShutdown()) {
                    this.pool = new ForkJoinPool();
                }
                ForkJoinTask<Void> parentTask = this.pool.submit(new SiteLinker(newPortal.getUrl()
                        , new URL(portalLink).getHost().replaceAll("^www.", "")
                        , newPortal
                        , new RepositoriesFactory(portalRepository, pageRepository)
                        , new ConnectionPerformance(userAgent, referrer)
                        , true));
                parentTask.join();
            } catch (MalformedURLException e) {
                newPortal.setStatusTime(new Date());
                newPortal.setStatus(IndexStatus.FAILED);
                portalRepository.save(newPortal);
                return responseError(response, e.toString());
            }
        }
        closeStatementConnection();
        this.pool.shutdown();
        response.setResult(true);
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        if (this.pool.getPoolSize() == 0)
            return responseError(response, "Индексация не запущена");
        else {
            SiteLinker.setStopCrawling(true);
            pool.shutdown();
            try {
                if ((!pool.awaitTermination(800, TimeUnit.MILLISECONDS)))
                pool.shutdownNow();
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }
            while (pool.getPoolSize() > 0) {};  //подождем, пока завершатся задачи пула потоков
            List<Portal> portals = portalRepository.findAll();
            portals.forEach(portal -> {
                portal.setStatus(IndexStatus.FAILED);
                portal.setLastError("Индексация остановлена пользователем");
                portal.setStatusTime(new Date());
                portalRepository.save(portal);
            });
            response.setResult(this.pool.isShutdown());
            return response;
        }
    }

    @Override
    public Boolean echo() {
        return true;
    }

    @Override
    public Integer getPoolSize() {
        return this.pool.getPoolSize();
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
                this.connection = (Connection) DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            this.statement.execute("delete from page where site_id = " + portal.getId());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
