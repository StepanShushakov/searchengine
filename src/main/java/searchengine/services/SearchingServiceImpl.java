package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.records.SearchingSettings;
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
        String lemmas = provideLemmasArrayToString(getLemmaArray(searchRequest.getQuery()));
        String siteUrl = searchRequest.getSite();
        String siteCondition = siteUrl == null ? "" : "\n\tand s.url = '" + siteUrl + "'";
        ArrayList<String> lemmas2Search = getLemmas2Search(lemmas
                ,siteCondition);
        String foundPageId = findPageId(lemmas2Search, siteUrl);
        if (foundPageId.isEmpty()) return blankResponse();
        getRelevance(provideLemmasArrayToString(lemmas2Search),
                foundPageId,
                new SearchingSettings(siteCondition,
                        searchRequest.getLimit()));
        return null;
    }

    private void getRelevance(String lemmas, String pageId, SearchingSettings ss) {
        try {
            createTempTables(lemmas, pageId, ss);
            long m1 = System.currentTimeMillis();
            ResultSet rs = statement.executeQuery("""
                    select
                    	p.uri,
                        p.content,
                        r.relevance
                    from temp_rel as r
                    left join temp_page as p
                    	on r.page_id = p.id
                    order by
                        r.relevance desc
                    limit 0,""" + ss.limit());
            long m2 = System.currentTimeMillis();
            System.out.println("main SQL " + (m2 - m1));
            while (rs.next()) {
                System.out.println("\turi = " + rs.getString("uri"));
                System.out.println("\trelevance = " + rs.getFloat("relevance"));
                String content = rs.getString("content");
                System.out.println("\ttitle = " + getTitleFromContent(content));
                //System.out.println("\tsnippet = " + getSnippetFromBodyContent(content));
                System.out.println();
            }
            dropTempTables();
            long m3 = System.currentTimeMillis();
            System.out.println("dropTempTables " + (m3 - m2));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTitleFromContent(String content) {
        return getTextFromTag(content,"title", 7);
    }

    private String getSnippetFromBodyContent(String content) {
        return getTextFromTag(content, "body", 6);
    }

    private String getTextFromTag(String content, String tag, int i) {
        return content.substring(content.indexOf("<" + tag + ">") + i, content.lastIndexOf("</" + tag +">"));
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

    private void createTempTables(String lemmas, String pageId, SearchingSettings ss) throws SQLException {
        long start = System.currentTimeMillis();
        statement.addBatch(queryLemmasTable(lemmas, ss.siteCondition()));
        statement.addBatch(queryPagesTable(pageId, ss.siteCondition()));
        statement.addBatch("create index id_index on temp_page (id)");
        statement.executeBatch();
        long b1 = System.currentTimeMillis();
        System.out.println("batch1 " + (b1 - start));
        statement.addBatch(queryAbsRelevanceTable());
        statement.executeBatch();
        long b2 = System.currentTimeMillis();
        System.out.println("batch2 " + (b2 - b1));
        statement.addBatch(queryMaxRelevanceTable());
        statement.executeBatch();
        long b3 = System.currentTimeMillis();
        System.out.println("batch3 " + (b3 - b2));
        statement.addBatch(queryRelRelevanceTable(ss.limit()));
        statement.addBatch("create index id_page_index on temp_rel (page_id)");
        statement.executeBatch();
        long b4 = System.currentTimeMillis();
        System.out.println("batch4 " + (b4 - b3));
    }

    private String queryRelRelevanceTable(int limit) {
        return """
                create temporary table temp_rel
                	select
                		ta.page_id,
                        ta.r / tm.max as relevance
                    from temp_abs ta
                    inner join temp_max tm
                		on true
                	order by
                		ta.r / tm.max desc
                	-- limit 0,""" + limit;
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

    private String queryPagesTable(String pageId, String siteCondition) {
        return """
                create temporary table temp_page
                	select
                		p.path uri,
                        p.content,
                        p.id
                	from page p
                	inner join site s
                	    on p.site_id = s.id""" + siteCondition + """
                \n\t\tand p.id in (""" + pageId + ")";
    }

    private String queryLemmasTable(String lemmas, String siteCondition) {
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
                        and l.lemma in (""" + lemmas + """
                )
                inner join site s
                    on l.site_id = s.id""" + siteCondition;
    }

    private SearchingResponse blankResponse() {
        closeStatementConnection();
        return new SearchingResponse();
    }

    private String provideLemmasArrayToString(ArrayList<String> lemmas) {
        return "'" + replaceStartEndArraysSymbols(lemmas.toString())
                .replaceAll(", ", "', '") + "'";
    }

    private String replaceStartEndArraysSymbols(String string) {
        return string.replace("[", "").replace("]", "");
    }

    private String findPageId(ArrayList<String> lemmas2Search, String siteUrl) {
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
                            + (pagesId.isEmpty() ? "" : "\n   and page_id in (" + pagesId + ")")
                            + (siteUrl == null ? "" : """
                    \ninner join site s
                        on l.site_id =  s.id
                        and s.url = '""" + siteUrl + "'")
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

    private ArrayList<String> getLemmaArray(String query) {
        ArrayList<String> lemmaArray = new ArrayList<>();
        lemmaArray.addAll(lemmaInstance.collectLemmas(query).keySet());
        return lemmaArray;
    }

    private ArrayList<LemmasFrequency> getLemmas2SearchOverInputQuery(String lemmas, String siteCondition) {
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

    private ArrayList<String> getLemmas2Search(String lemmas, String siteCondition) {
        try {
            if (this.connection == null ||
                    this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            ResultSet rs = this.statement.executeQuery("""
                    with page_count as (
                    select count(p.id) `value`
                    from page p
                    inner join site s\non p.code = 200\n	and p.site_id = s.id""" + siteCondition + """
                    )
                    select
                    	l.lemma,\n    sum(l.frequency) / max(pc.`value`) part
                    from lemma l
                    inner join site s\non l.site_id = s.id\n	and l.lemma in (""" + lemmas + ")" + siteCondition + """  
                    \ninner join page_count pc
                    group by l.lemma
                    having sum(l.frequency) / max(pc.`value`) < 0.85
                    order by part
                    """);
            ArrayList<String> result = new ArrayList<>();
            while (rs.next()) result.add(rs.getString("lemma"));
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
