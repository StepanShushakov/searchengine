package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.services.supportingServices.CrawlStarter;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.services.supportingServices.Link;
import searchengine.services.supportingServices.SiteLinker;
import searchengine.model.*;
import searchengine.records.ConnectionPerformance;
import searchengine.records.PageDescription;
import searchengine.records.RepositoriesFactory;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
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
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
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
        if (SiteLinker.indexingStarted()) return responseError(response, "Индексация уже запущена");
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        SiteLinker.setIndexingStarted(true);
        for (Site site : sites.getSites()) {
            String portalUrl = site.getUrl();
            Portal portal = portalRepository.findByUrl(portalUrl);
            if (portal != null) {
                deletePortalPages(portal);
                portalRepository.delete(portal);
            }
            Portal newPortal = createPortalBySite(site, portalUrl);
            SiteLinker.initVerifySer();
            CrawlStarter.setRepositoriesFactory(new RepositoriesFactory(portalRepository,
                                                                            pageRepository,
                                                                            lemmaRepository,
                                                                            indexRepository));
            CrawlStarter.setConnectionPerformance(new ConnectionPerformance(userAgent, referrer));
            executor.submit(new CrawlStarter(newPortal));
        }
        executor.shutdown();
        closeStatementConnection();
        return response;
    }

    private Portal createPortalBySite(Site site, String portalUrl) {
        Portal newPortal = new Portal();
        newPortal.setName(site.getName());
        newPortal.setUrl(portalUrl);
        newPortal.setStatus(IndexStatus.INDEXING);
        newPortal.setStatusTime(new Date());
        portalRepository.save(newPortal);
        return newPortal;
    }

    @Override
    public IndexingResponse stopIndexing() {
        IndexingResponse response = new IndexingResponse();
        ForkJoinPool pool = CrawlStarter.getPool();
        if (!SiteLinker.indexingStarted())
            return responseError(response, "Индексация не запущена");
        else {
            SiteLinker.setStopCrawling(true);
            SiteLinker.setIndexingStarted(false);
            List<Portal> indexingPortals = portalRepository.findByStatus(IndexStatus.INDEXING);
            pool.shutdown();
            try {
                if ((!pool.awaitTermination(800, TimeUnit.MILLISECONDS))) pool.shutdownNow();
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }
            while (pool.getPoolSize() > 0);  //подождем, пока завершатся задачи пула потоков
            indexingPortals.forEach(portal -> {
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
        URL url;
        try {
            url = Link.getUrlFromString(indexPage.getUrl());
        } catch (MalformedURLException e) {
            return responseError(response, e.toString());
        }
        Portal portal = findPortalByMainUrl(Link.getPortalMainUrl(url));
        if (portal == null) return responseError(response, "Данная страница находится за пределами сайтов,\n" +
                    "указанных в конфигурационном файле");

        List<Page> pages = pageRepository.findByPortalAndPath(portal, url.getPath());

        if (pages.size() == 0) {
            new Link(new PageDescription(url.getProtocol() + "://" + url.getHost() + url.getPath()
                                                        ,portal, url.getHost().equals(portal.getUrl()))
                        ,new RepositoriesFactory(portalRepository, pageRepository, lemmaRepository, indexRepository)
                        ,new ConnectionPerformance(this.userAgent, this.referrer));
        } else {
            for (Page page: pages) {
                Link.setRepositories(new RepositoriesFactory(portalRepository, pageRepository, lemmaRepository, indexRepository));
                Link.setConnectionPerformance(new ConnectionPerformance(userAgent, referrer));
                Link.indexPage(page, Link.getDoc(page, portal),false);
            }
        }
        return response;
    }

    private Portal findPortalByMainUrl(String portalUrl){
        Portal portal = portalRepository.findByUrl(portalUrl);
        if (portal == null) {
            Portal newPortal = null;
            for (Site site: sites.getSites()) {
                if (site.getUrl().equals(portalUrl)) {
                    newPortal = createPortalBySite(site, portalUrl);
                    break;
                }
            }
            portal = newPortal;
        }
        return portal;
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
            if (this.statement != null && !statement.isClosed()) this.statement.close();
            if (this.connection != null && !connection.isClosed()) this.connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void deletePortalPages(Portal portal) {
        try {
            if (this.connection == null ||
                    this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            this.connection.setAutoCommit(false);
            this.statement.execute( "delete from `index` where page_id in " +
                    "(select id from page where site_id = " + portal.getId() + ")");
            this.statement.execute("delete from lemma where site_id = " + portal.getId());
            this.statement.execute("delete from page where site_id = " + portal.getId());
            this.connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
