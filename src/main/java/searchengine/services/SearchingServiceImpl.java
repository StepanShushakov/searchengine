package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.DetailedSearchingItem;
import searchengine.model.Page;
import searchengine.records.PageRelevance;
import searchengine.records.SearchingSettings;
import searchengine.repositories.PageRepository;
import searchengine.services.supportingservices.LemmaFinder;
import searchengine.dto.searching.SearchingResponse;
import searchengine.response.SearchRequest;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class SearchingServiceImpl implements SearchingService{
    @Value("${spring.datasource.username}")
    private String DBUserName;
    @Value("${spring.datasource.password}")
    private String DBPassword;
    @Value("${spring.datasource.url}")
    private String DBUrl;
    private Connection connection;
    private Statement statement;

    private final PageRepository pageRepository;

    private static final LemmaFinder lemmaInstance;

    static {
        try {
            lemmaInstance = LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SearchingResponse search(SearchRequest searchRequest) {
        String query = searchRequest.getQuery();
        if (query.isBlank()) return errorFoundResponse("Задан пустой поисковый запрос");
        String lemmas = provideCollectionToQueryParameter(getLemmasSet(query));
        String siteUrl = searchRequest.getSite();
        String siteCondition = siteUrl == null ? "" : "\n\tand s.url = '" + siteUrl + "'";
        HashSet<String> lemmas2Search = getLemmas2Search(lemmas
                ,siteCondition);
        String foundPageId = findPageId(lemmas2Search, siteUrl);
        if (foundPageId.isEmpty()) return errorFoundResponse("Ничего не найдено");
        ArrayList<PageRelevance> foundRelevance = getRelevance(provideCollectionToQueryParameter(lemmas2Search),
                foundPageId,
                new SearchingSettings(searchRequest.getOffset(), searchRequest.getLimit()));
        closeStatementConnection();

        SearchingResponse response = new SearchingResponse();
        response.setCount(foundRelevance.get(0).totalCount());
        response.setData(processingFoundPage(foundRelevance, lemmas2Search));
        response.setResult(true);
        return response;
    }

    private List<DetailedSearchingItem> processingFoundPage(ArrayList<PageRelevance> foundRelevance, Set<String> lemmas2Search) {
        List<DetailedSearchingItem> detailed = new ArrayList<>();
        for (PageRelevance fr: foundRelevance) {
            DetailedSearchingItem item = new DetailedSearchingItem();
            Page page = fr.page();
            Document doc = Jsoup.parse(page.getContent());
            item.setSite(page.getPortal().getUrl());
            item.setSiteName(page.getPortal().getName());
            item.setUri(page.getPath());
            item.setTitle(doc.title());
            item.setSnippet(getSnippet(doc.select("title, body").text(), lemmas2Search));
            item.setRelevance(fr.relevance());
            detailed.add(item);
        }
        return detailed;
    }
    private ArrayList<PageRelevance> getRelevance(String lemmas, String pageId, SearchingSettings ss) {
        try {
            ResultSet rs = statement.executeQuery(getRelevanceQueryText(lemmas, pageId, ss));
            ArrayList<PageRelevance> result = new ArrayList<>();
            while (rs.next()) {
                Optional<Page> optPage = pageRepository.findById(rs.getInt("page_id"));
                if (optPage.isPresent()) {
                    result.add(new PageRelevance(optPage.get(),
                            rs.getFloat("relevance"),
                            rs.getInt("total_count")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getRelevanceQueryText(String lemmas, String pageId, SearchingSettings ss) {
        return """
                    with temp_lemmas as (select
                            i.page_id,
                            i.rank
                        from lemma l
                        inner join `index` i
                            on l.id = i.lemma_id
                            and l.lemma in (""" + lemmas + ")\n\t\tand i.page_id in (" +pageId + """
                    )),
                    temp_abs as (select
                            p.id page_id,
                            sum(l.rank) r
                        from temp_lemmas l
                        inner join page p
                            on l.page_id = p.id
                        group by p.id),
                    temp_max as (
                        select max(r) max
                        from temp_abs),
                    page_count as (select count(distinct tl.page_id) as count from temp_lemmas as tl)
                    select
                            ta.page_id,
                            ta.r / tm.max as relevance,
                            pc.count as total_count
                        from temp_abs ta
                        inner join temp_max tm
                        inner join page_count as pc
                        order by ta.r / tm.max desc
                    limit\s""" + ss.offset() + " ," + ss.limit();
    }

    private String getSnippet(String content, Set<String> lemmas2Search) {
        String[] words = content.split(" +");
        int j = 0;
        Integer k = null;
        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^А-Яа-я]", "");
            if (!word.isEmpty() && lemmas2Search.contains(lemmaInstance.getNormalForm(word.toLowerCase()))) {
                words[i] = words[i].replace(word, "<b>" + word + "</b>");
                if (j == 0) {
                    j = 1;
                    k = i;
                }
            }
            if (j > 0) j++;
            if (j == 15) break;
        }
        int start;
        if (k < 14) start = 0;
        else start = k - 14;
        int end = k + 15;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i == words.length) break;
            if (i != start) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private SearchingResponse errorFoundResponse(String errorText) {
        closeStatementConnection();
        SearchingResponse response = new SearchingResponse();
        response.setResult(false);
        response.setError(errorText);
        return response;
    }

    private String provideCollectionToQueryParameter(Set<String> lemmas) {
        return "'" + replaceStartEndArraysSymbols(lemmas.toString())
                .replaceAll(", ", "', '") + "'";
    }

    private String replaceStartEndArraysSymbols(String string) {
        return string.replace("[", "").replace("]", "");
    }

    private String findPageId(HashSet<String> lemmas2Search, String siteUrl) {
        AtomicReference<String> pagesId = new AtomicReference<>("");
        for (String lf : lemmas2Search) {
            String newPageId = searchPages(lf, pagesId.get(), siteUrl);
            pagesId.set(newPageId);
            if (newPageId.isEmpty()) break;
        }
        return pagesId.toString();
    }

    private String searchPages(String lemma, String pagesId, String siteUrl) {
        try {
            ResultSet rs = this.statement.executeQuery("""
                    select
                        i.page_id
                    from lemma l
                    inner join `index` i
                        on l.id = i.lemma_id
                        and l.lemma = '""" + lemma + "'"
                            + (pagesId.isEmpty() ? "" : "\n   and page_id in (" + pagesId + ")") + """
                    \ninner join site s
                        on l.site_id =  s.id
                        and s.status = 'INDEXED'""" + (siteUrl == null ? "" : "\n\tand s.url = '" + siteUrl + "'")
                    );
            StringBuilder newId = new StringBuilder();
            while (rs.next()) {
                newId.append((newId.length() == 0) ? "" : ", ").append(rs.getInt("page_id"));
            }
            return newId.toString();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getLemmasSet(String query) {
        return lemmaInstance.collectLemmas(query).keySet();
    }

    private HashSet<String> getLemmas2Search(String lemmas, String siteCondition) {
        try {
            if (this.connection == null ||
                    this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            ResultSet rs = this.statement.executeQuery(getLemmas2SearchQueryText(lemmas, siteCondition));
            HashSet<String> result = new HashSet<>();
            while (rs.next()) result.add(rs.getString("lemma"));
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getLemmas2SearchQueryText(String lemmas, String siteCondition) {
        return """
                    with page_count as (
                    select count(p.id) `value`
                    from page p
                    inner join site s
                    on p.code = 200
                        and p.site_id = s.id and s.status = 'INDEXED'""" + siteCondition + """
                    )
                    select
                        l.lemma,
                        sum(l.frequency) / max(pc.`value`) part
                    from lemma l
                    inner join site s
                    on l.site_id = s.id
                        and s.status = 'INDEXED'
                    	and l.lemma in (""" + lemmas + ")" + siteCondition + """  
                    
                    inner join page_count pc
                    group by l.lemma having sum(l.frequency) / max(pc.`value`) < 0.85
                    order by part
                    """;
    }

    private void closeStatementConnection() {
        try {
            if (statement != null && !statement.isClosed()) statement.close();
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
