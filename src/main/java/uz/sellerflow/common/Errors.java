package uz.sellerflow.common;

/** Domen xatolari. Kod — botda foydalanuvchiga tushunarli xabar tanlash uchun. */
public final class Errors {
    private Errors() {}

    /** Obyekt topilmadi yoki boshqa tenantga tegishli (bir xil javob — IDOR himoyasi). */
    public static class NotFound extends RuntimeException {
        public NotFound(String what) { super(what + " not found"); }
    }

    /** Optimistic lock — parallel tahrir. */
    public static class Conflict extends RuntimeException {
        public Conflict(String msg) { super(msg); }
    }

    /** Biznes validatsiyasi. */
    public static class Validation extends RuntimeException {
        private final String code;
        public Validation(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }

    /** Akkaunt holati amalga ruxsat bermaydi. */
    public static class Forbidden extends RuntimeException {
        public Forbidden(String msg) { super(msg); }
    }
}
