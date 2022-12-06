package searchengine.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

public interface PortalRepository extends JpaRepository<Portal, Integer> {
    @Query("select p from Portal p where p.name = ?1 and p.url = ?2")
    Portal findByNameAndUrl(@NonNull String name, @NonNull String url);
}
