package searchengine.records;

public record LemmasFrequency(String lemma, int frequency) {
    @Override
    public String toString() {
        return "'" + lemma + "'";
    }
}
