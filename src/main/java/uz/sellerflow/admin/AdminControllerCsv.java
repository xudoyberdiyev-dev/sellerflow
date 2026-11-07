package uz.sellerflow.admin;

/** Testlar uchun ochiq kirish nuqtasi: CSV yacheyka xavfsizlash (AdminController.csvCell). */
public final class AdminControllerCsv {
    private AdminControllerCsv() {}
    public static String cell(String v) { return AdminController.csvCell(v); }
}
