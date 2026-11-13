package uz.sellerflow.telegram;

/** Testlar uchun: paket ichidagi yordamchilarga kirish. */
public final class HttpTelegramGatewayAccess {
    private HttpTelegramGatewayAccess() {}

    public static boolean isReadTimeout(Throwable e) {
        return HttpTelegramGateway.isReadTimeout(e);
    }
}
