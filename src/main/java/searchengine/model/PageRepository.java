package searchengine.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import searchengine.model.Page;
import searchengine.model.Portal;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Integer> {
    List<Page> findByPortalAndPath(@NonNull Portal portal, @NonNull String path);
    void deleteByPortal(Portal portal);
}
