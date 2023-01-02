package searchengine.records;

import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.PortalRepository;

public record RepositoriesFactory(
        PortalRepository portalRepository,
        PageRepository pageRepository,
        LemmaRepository lemmaRepository) {
}
