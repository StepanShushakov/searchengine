package searchengine;

import searchengine.model.Page;
import searchengine.model.PageRepository;
import searchengine.model.Portal;
import searchengine.model.PortalRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;

public class SiteLinker extends RecursiveAction {
//    private JpaService jpaService = new JpaService();
    private String url;
    private String host;
    private Portal portal;
    private Link link;
    private PortalRepository portalRepository;
    private PageRepository pageRepository;

    public SiteLinker(String url,
                      String host,
                      Portal portal,
                      PortalRepository portalRepository,
                      PageRepository pageRepository) {
        this.url = url;
        this.host = host;
        this.portal = portal;
        this.portalRepository = portalRepository;
        this.pageRepository = pageRepository;
        Page page = new Page();
        page.setPortal(portal);
        this.link = new Link(url, host, page, portal, portalRepository, pageRepository);
    }

    @Override
    protected void compute() {
        List<SiteLinker> taskList = new ArrayList<>();
        List<String> childrenLinks = this.link.getChildrenLinks();
        for (int i = 0; i < childrenLinks.size(); i++) {
            String link = childrenLinks.get(i);
            if (linkIsAdded(link)) continue;
            SiteLinker task = new SiteLinker(link
                    ,this.host
                    ,this.portal
                    ,this.portalRepository
                    ,this.pageRepository);
            task.fork();
            taskList.add(task);
        }
        for (SiteLinker task : taskList) {
            task.join();
        }
    }

    private boolean linkIsAdded(String link) {
        String path = "";
        try {
            path = new URL(link).getPath();
            if (path.isEmpty()) path = "/";
        } catch (MalformedURLException e) {
            return true;    //если произойдёт исключение, скажем,
            // что страница уже добавлена,
            // что бы не пытаться её разбирать
        }
        return pageRepository.findByPortalAndPath(this.portal, path) != null;
    }
}