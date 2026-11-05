package uz.sellerflow.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellerflow")
public record SellerFlowProperties(Telegram telegram, Admin admin, Terms terms, Draft draft, Support support) {

    public record Telegram(String botToken, String apiBaseUrl, boolean pollingEnabled, int pollingTimeoutSeconds) {}

    public record Admin(String apiKey) {}

    public record Terms(String version) {}

    public record Draft(int ttlHours) {}

    public record Support(String defaultLabel, String defaultUrl) {}
}
