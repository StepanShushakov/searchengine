package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexStatus;
import searchengine.model.Portal;

import java.util.List;

@Repository
public interface PortalRepository extends JpaRepository<Portal, Integer> {
    long countByStatusNot(IndexStatus status);

    Portal findByUrl(String s);

    List<Portal> findByStatus(IndexStatus indexStatus);
}
