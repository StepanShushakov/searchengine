package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Portal;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query("select count(p) from Page p where p.portal = ?1 and p.code = 200")
    int countByPortal(Portal portal);
    List<Page> findByPortalAndPath(@NonNull Portal portal, @NonNull String path);
    void deleteByPortal(Portal portal);
}
