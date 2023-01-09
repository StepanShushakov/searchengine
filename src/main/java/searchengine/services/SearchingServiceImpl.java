package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.LemmaFinder;
import searchengine.dto.searching.SearchingResponse;
import searchengine.response.SearchRequest;

import java.io.IOException;
import java.sql.*;
import java.util.*;

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
        lemmas = getNeedLemmaSet(lemmas.toString()
                    .replace("[", "'")
                    .replace("]","'")
                    .replaceAll(", ", "', '")
                ,(searchRequest.getSite() == null ? "" : "\nand s.url = ?"));

        closeStatementConnection();
        return null;
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

    private Set<String> getNeedLemmaSet(String lemmas, String site) {
        try {
            if (this.connection == null ||
                    this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(DBUrl, DBUserName, DBPassword);
                this.statement = connection.createStatement();
            }
            ResultSet rs = this.statement.executeQuery("""
                select\s
                	l.lemma, l.frequency
                from lemma l
                left join site s
                    on l.site_id = s.id
                where l.lemma in (""" + lemmas + ")" + site);
            double totalCount = 0;
            HashMap<String, Integer> result = new HashMap<>();
            while (rs.next()) {
                String key = rs.getString("lemma");
                result.put(key, (result.get(key) == null ? 0 : result.get(key)) + rs.getInt("frequency"));
                totalCount = totalCount + rs.getInt("frequency");
            }
            closeStatementConnection();
            removeOftenKeys(result, totalCount);
            return result.keySet();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeOftenKeys(HashMap<String, Integer> result, double totalCount) {
        ArrayList<String> keys2Remove = new ArrayList<>();
        for (String key: result.keySet()) {
            if (result.get(key) / totalCount >= 0.4) keys2Remove.add(key);
        }
        keys2Remove.forEach(k -> result.remove(k));
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
