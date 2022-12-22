package searchengine;

import searchengine.repositories.PageRepository;
import searchengine.repositories.PortalRepository;

public class RepositoriesFactory {
    private PortalRepository portalRepository;
    private PageRepository pageRepository;

    public RepositoriesFactory(PortalRepository portalRepository, PageRepository pageRepository) {
        this.portalRepository = portalRepository;
        this.pageRepository = pageRepository;
    }

    public PortalRepository getPortalRepository() {
        return portalRepository;
    }

    public PageRepository getPageRepository() {
        return pageRepository;
    }
}
