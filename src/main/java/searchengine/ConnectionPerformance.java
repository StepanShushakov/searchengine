package searchengine;

public class ConnectionPerformance {
    private String userAgent;
    private String referrer;

    public ConnectionPerformance(String userAgent, String referrer) {
        this.userAgent = userAgent;
        this.referrer = referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getReferrer() {
        return referrer;
    }
}
