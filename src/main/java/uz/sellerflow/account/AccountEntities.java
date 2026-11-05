package uz.sellerflow.account;

/** Akkaunt domeni enumlari (SF-PRD-01). */
public final class AccountEntities {
    private AccountEntities() {}

    public enum AccountStatus { ONBOARDING, ACTIVE, SUSPENDED, DELETION_REQUESTED, CLOSED }

    /** 7 ta birlamchi kanal (SF-PRD-01, 5.2). */
    public enum SalesChannel {
        UZUM("Uzum Market"),
        WILDBERRIES("Wildberries"),
        OZON("Ozon"),
        INSTAGRAM("Instagram"),
        TELEGRAM_SALES("Telegram orqali savdo"),
        OFFLINE_STORE("Offline do‘kon"),
        OTHER("Boshqa");

        private final String label;
        SalesChannel(String label) { this.label = label; }
        public String label() { return label; }
    }
}
