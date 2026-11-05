package uz.sellerflow.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sellerflow")
public record SellerFlowProperties(Telegram telegram, Admin admin, Terms terms, Draft draft, Support support,
                                   Deletion deletion, RateLimit rateLimit) {

    public record Telegram(String botToken, String apiBaseUrl, boolean pollingEnabled, int pollingTimeoutSeconds) {}

    public record Admin(String bootstrapEmail, String bootstrapPassword) {}

    /** graceDays — o'chirish so'rovidan keyin CLOSED gacha; purgeData — yurist qaroriga qadar false. */
    public record Deletion(int graceDays, boolean purgeData) {}

    public record RateLimit(int maxUpdates, int windowSeconds) {}

    public record Terms(String version) {}

    public record Draft(int ttlHours) {}

    public record Support(String defaultLabel, String defaultUrl) {}
}
