package searchengine.model;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "site")
public class Portal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "enum('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private IndexStatus status;

    @Column(name = "status_time", nullable = false)
    private Date statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String name;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "portal")
    private List<Page> pages;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "portal")
    private List<Lemma> lemmas;

    transient private boolean errorMainPage;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public IndexStatus getStatus() {
        return status;
    }

    public void setStatus(IndexStatus status) {
        this.status = status;
    }

    public Date getStatusTime() {
        return statusTime;
    }

    public void setStatusTime(Date statusTime) {
        this.statusTime = statusTime;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }

    public List<Lemma> getLemmas() {
        return lemmas;
    }

    public void setLemmas(List<Lemma> lemmas) {
        this.lemmas = lemmas;
    }

    public boolean isErrorMainPage() {
        return errorMainPage;
    }

    public void setErrorMainPage(boolean errorMainPage) {
        this.errorMainPage = errorMainPage;
    }

    @Override
    public String toString() {
        return url;
    }
}
