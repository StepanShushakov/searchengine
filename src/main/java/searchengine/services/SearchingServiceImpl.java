package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.services.supportingServices.LemmaFinder;
import searchengine.dto.searching.SearchingResponse;
import searchengine.records.LemmasFrequency;
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
        String lemmas = provideLemmasSetToString(getLemmaSet(searchRequest.getQuery()));
        String siteUrl = searchRequest.getSite();
        ArrayList<LemmasFrequency> lemmas2Search = getLemmas2Search(lemmas
                ,(siteUrl == null ? "" : "\nand s.url = '" + siteUrl + "'"));
        String foundPageId = findPageId(lemmas2Search, siteUrl);
        if (foundPageId.isEmpty()) return blankResponse();
        getRelevance(replaceStartEndArraysSymbols(lemmas2Search.toString()), foundPageId);
        return null;
    }

    private void getRelevance(String lemmas, String pageId) {
        try {
            createTempTables(lemmas, pageId);
            ResultSet rs = statement.executeQuery("""
                    select
                    	p.uri,
                        p.content,
                        r.relevance
                    from temp_rel as r
                    left join temp_page as p
                    	on r.page_id = p.id""");
            while (rs.next()) {
                System.out.println("\turi = " + rs.getString("uri"));
                System.out.println("\trelevance = " + rs.getFloat("relevance"));
                String content = rs.getString("content");
                System.out.println("\ttitle = " + getTitleFromContent(content));
                System.out.println();
            }
            dropTempTables();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTitleFromContent(String content) {
        return content.substring(content.indexOf("title") + 6, content.lastIndexOf("</title>"));
    }

    private void dropTempTables() throws SQLException {
        statement.clearBatch();
        statement.addBatch("drop temporary table temp_lemmas");
        statement.addBatch("drop temporary table temp_page");
        statement.addBatch("drop temporary table temp_abs");
        statement.addBatch("drop temporary table temp_max");
        statement.addBatch("drop temporary table temp_rel");
        statement.executeBatch();
    }

    private void createTempTables(String lemmas, String pageId) throws SQLException {
        statement.addBatch(queryLemmasTable(lemmas));
        statement.addBatch(queryPagesTable(pageId));
        statement.executeBatch();
        statement.addBatch(queryAbsRelevanceTable());
        statement.executeBatch();
        statement.addBatch(queryMaxRelevanceTable());
        statement.executeBatch();
        statement.addBatch(queryRelRelevanceTable());
        statement.executeBatch();
    }

    private String queryRelRelevanceTable() {
        return """
                create temporary table temp_rel
                	select
                		ta.page_id,
                        ta.r / tm.max as relevance
                    from temp_abs ta
                    inner join temp_max tm
                		on true
                	order by
                		ta.r / tm.max desc""";
    }

    private String queryMaxRelevanceTable() {
        return """
                create temporary table temp_max
                	select max(r) max
                    from temp_abs""";
    }

    private String queryAbsRelevanceTable() {
        return """
                create temporary table temp_abs
                	select p.id page_id,
                    sum(l.rank) r
                	from temp_lemmas l
                	inner join temp_page p
                		on l.page_id = p.id
                	group by
                    p.id""";
    }

    private String queryPagesTable(String pageId) {
        return """
                create temporary table temp_page
                	select
                		p.path uri,
                        p.content,
                        p.id
                	from page p
                    where p.id in (""" + pageId + ")";
    }

    private String queryLemmasTable(String lemmas) {
        return """
                create temporary table temp_lemmas
                	select
                		l.id lemma_id,
                        i.page_id,
                        l.lemma,
                        i.rank
                	from lemma l
                	inner join `index` i
                    on l.id = i.lemma_id
                	where l.lemma in (""" + lemmas + ")";
    }

    private SearchingResponse blankResponse() {
        closeStatementConnection();
        return new SearchingResponse();
    }

    private String provideLemmasSetToString(Set<String> lemmas) {
        return "'" + replaceStartEndArraysSymbols(lemmas.toString())
                .replaceAll(", ", "', '") + "'";
    }

    private String replaceStartEndArraysSymbols(String string) {
        return string.replace("[", "").replace("]", "");
    }

    private String findPageId(ArrayList<LemmasFrequency> lemmas2Search, String siteUrl) {
        AtomicReference<String> pagesId = new AtomicReference<>("");
        for (LemmasFrequency lf : lemmas2Search) {
            String newPageId = searchPages(lf.lemma(), pagesId.get(), siteUrl);
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
                            + (pagesId.isEmpty() ? "" : "\n   and page_id in (" + pagesId + ")"
                            + (siteUrl == null ? "" : """
                    \ninner join site
                        on i.site_id =  s.id
                        and s.url = '""" + siteUrl + "'"))
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

    private Set<String> getLemmaSet(String query) {
        return lemmaInstance.collectLemmas(query).keySet();
    }

    private ArrayList<LemmasFrequency> getLemmas2Search(String lemmas, String siteCondition) {
        try {
            if (this.connection == null ||
                    this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            ResultSet rs = this.statement.executeQuery("""
                select
                	l.lemma, sum(l.frequency) as frequency
                from lemma l
                left join site s
                    on l.site_id = s.id
                where l.lemma in (""" + lemmas + ")" + siteCondition + """
                group by l.lemma
                order by frequency""");
            double totalCount = 0;
            ArrayList<LemmasFrequency> result = new ArrayList<>();
            while (rs.next()) {
                String key = rs.getString("lemma");
                result.add(new LemmasFrequency(key, rs.getInt("frequency")));
                totalCount = totalCount + rs.getInt("frequency");
            }
            removeOftenKeys(result, totalCount);
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeOftenKeys(ArrayList<LemmasFrequency> result, double totalCount) {
        if (result.size() == 1) return;
        ArrayList<LemmasFrequency> elements2Remove = new ArrayList<>();
        result.forEach(lF -> {if (lF.frequency() / totalCount >= 0.85) elements2Remove.add(lF);});
        elements2Remove.forEach(result::remove);
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
