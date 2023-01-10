package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.LemmaFinder;
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
    @Override
    public SearchingResponse search(SearchRequest searchRequest) {
        Set <String> lemmas = getLemmaSet(searchRequest.getQuery());
        String siteUrl = searchRequest.getSite();
        ArrayList<LemmasFrequency> lemmas2Search = getLemmas2Search(lemmas.toString()
                    .replace("[", "'")
                    .replace("]","'")
                    .replaceAll(", ", "', '")
                ,(siteUrl == null ? "" : "\nand s.url = '" + siteUrl + "'"));
        AtomicReference<String> pagesId = new AtomicReference<>("");
        for (LemmasFrequency lf: lemmas2Search) {
            String newPageId = searchPages(lf.lemma(), pagesId.get(), siteUrl);
            if (newPageId.isEmpty()) break;
            pagesId.set(newPageId);
        }
        closeStatementConnection();
        return null;
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
        Set<String> lemmas;
        try {
            lemmas = LemmaFinder.getInstance().collectLemmas(query).keySet();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return lemmas;
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
        ArrayList<LemmasFrequency> elements2Remove = new ArrayList<>();
        result.forEach(lF -> {if (lF.frequency() / totalCount >= 0.4) elements2Remove.add(lF);});
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
