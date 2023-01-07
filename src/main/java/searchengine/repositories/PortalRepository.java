package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexStatus;
import searchengine.model.Portal;

@Repository
public interface PortalRepository extends JpaRepository<Portal, Integer> {
    long countByStatusNot(IndexStatus status);

    Portal findByUrl(String s);
}
