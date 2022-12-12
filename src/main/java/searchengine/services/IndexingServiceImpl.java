package searchengine.services;

import liquibase.pro.packaged.A;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.ConnectionPerformance;
import searchengine.RepositoriesFactory;
import searchengine.SiteLinker;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;

import javax.sound.sampled.Port;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final SitesList sites;

    @Autowired
    private PortalRepository portalRepository;
    @Autowired
    private PageRepository pageRepository;

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
        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        sites.getSites().forEach(site -> {
            Portal portal = portalRepository.findByNameAndUrl(site.getName(), site.getUrl());
            if (portal != null){
                //pageRepository.deleteByPortal(portal);
                try {
                    deletePagesByPortal(portal);
                } catch (SQLException e) {
                    throw new RuntimeException(e);  //: будем возвращать ошибку
                }
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
                pool.submit(new SiteLinker(newPortal.getUrl()
                        ,new URL(portalLink).getHost().replaceAll("^www.", "")
                        ,newPortal
                        ,new RepositoriesFactory(portalRepository, pageRepository)
                        ,new ConnectionPerformance(userAgent, referrer)));
            } catch (MalformedURLException e) {
//                throw new RuntimeException(e);
                newPortal.setStatusTime(new Date());
                newPortal.setStatus(IndexStatus.FAILED);
                portalRepository.save(newPortal);
            }
        });
        closeStatementConnection();

        IndexingResponse response = new IndexingResponse();
        response.setSites(sites);
        return response;
    }

    private void closeStatementConnection() {
        try {
            if (this.statement != null) this.statement.close();
            if (this.connection != null) this.connection.close();
        } catch (SQLException e) {
//            throw new RuntimeException(e);
        }
    }

    private void deletePagesByPortal(Portal portal) throws SQLException {
        if (this.connection == null) {
            this.connection = (Connection) DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
            this.statement = connection.createStatement();
        }
        this.statement.execute("delete from page where site_id = " + portal.getId());
    }
}
