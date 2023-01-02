package searchengine.model;

import javax.persistence.*;
import javax.persistence.Index;
import java.util.List;

@Entity
@Table(/*indexes = @Index(name="index_path", columnList = "path")*/)
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Portal portal;

    @Column(columnDefinition = "TEXT NOT NULL, UNIQUE KEY unique_page_index (site_id,path(255))")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT 'HTML-код страницы'", nullable = false)
    private String content;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "page")
    private List<IndexEntity> index;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Portal getPortal() {
        return portal;
    }

    public void setPortal(Portal portal) {
        this.portal = portal;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
