package searchengine.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import searchengine.model.Page;
import searchengine.model.Portal;

public interface PageRepository extends JpaRepository<Page, Integer> {
    Page findByPortalAndPath(@NonNull Portal portal, @NonNull String path);
}