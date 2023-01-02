package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Portal;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Query("select l from Lemma l where l.portal = ?1 and l.lemma = ?2")
    List<Lemma> findByPortalAndLemma(@NonNull Portal portal, @NonNull String lemma);
}
