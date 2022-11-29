package searchengine.model;

import javax.persistence.*;

@Entity
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private int id;

//    @JoinColumn(name = "page_id", nullable = false)
//    private Page page;
//
//    @JoinColumn(name = "lemma_id", nullable = false)
//    private Lemma lemma;

    @Column(nullable = false)
    private float rank;
}
