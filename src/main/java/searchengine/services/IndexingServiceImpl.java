package searchengine.services;

import liquibase.pro.packaged.A;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.SiteLinker;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.IndexStatus;
import searchengine.model.PageRepository;
import searchengine.model.Portal;
import searchengine.model.PortalRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final SitesList sites;

    @Autowired
    private PortalRepository portalRepository;
    @Autowired
    private PageRepository pageRepository;

    @Override
    public IndexingResponse startIndexing() {
        sites.getSites().forEach(site -> {
            Portal portal = portalRepository.findByNameAndUrl(site.getName(), site.getUrl());
            if (portal != null){
                portalRepository.delete(portal);
            }
            Portal newPortal = new Portal();
            newPortal.setName(site.getName());
            String portalLink = site.getUrl();
            newPortal.setUrl(portalLink);
            newPortal.setStatus(IndexStatus.INDEXING);
            newPortal.setStatusTime(new Date());
            portalRepository.save(newPortal);
            ForkJoinPool pool = new ForkJoinPool();
            try {
                pool.invoke(new SiteLinker(newPortal.getUrl()
                        ,new URL(portalLink).getHost().replaceAll("^www.", "")
                        ,newPortal
                        ,portalRepository
                        ,pageRepository));
            } catch (MalformedURLException e) {
//                throw new RuntimeException(e);
                newPortal.setStatusTime(new Date());
                newPortal.setStatus(IndexStatus.FAILED);
                portalRepository.save(newPortal);
            }

        });


        IndexingResponse response = new IndexingResponse();
        response.setSites(sites);
        return response;
    }
}
