package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.IndexStatus;
import searchengine.model.Portal;
import searchengine.model.PortalRepository;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService{

    private final SitesList sites;

    @Autowired
    private PortalRepository portalRepository;

    @Override
    public IndexingResponse startIndexing() {
        sites.getSites().forEach(site -> {
            Portal portal = portalRepository.findByNameAndUrl(site.getName(), site.getUrl());
            if (portal != null){
                portalRepository.delete(portal);
            }
            Portal newPortal = new Portal();
            newPortal.setName(site.getName());
            newPortal.setUrl(site.getUrl());
            newPortal.setStatus(IndexStatus.INDEXING);
            newPortal.setStatusTime(new Date());
            portalRepository.save(newPortal);
        });


        IndexingResponse response = new IndexingResponse();
        response.setSites(sites);
        return response;
    }
}
