package searchengine.model;

import javax.persistence.*;

@Entity
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private int id;

//    @OneToOne(fetch = FetchType.LAZY)
//    private Page page;
//
//    @OneToOne(fetch = FetchType.LAZY)
//    private Lemma lemma;

    @Column(name = "lemma_rank", nullable = false)
    private float rank;
}
