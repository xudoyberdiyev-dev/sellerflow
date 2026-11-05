package uz.sellerflow.bot;

import org.springframework.stereotype.Component;
import uz.sellerflow.access.AccessGrantService;
import uz.sellerflow.access.AccessGrantService.Decision;
import uz.sellerflow.access.AccessGrantService.Grant;
import uz.sellerflow.access.AccessGrantService.Status;
import uz.sellerflow.telegram.Tg.Kb;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Sotuvchining admin kirish so'roviga javobi: g:{grantId}:{a|d|r}. */
@Component
public class AccessHandler {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Tashkent"));

    private final AccessGrantService grants;

    public AccessHandler(AccessGrantService grants) {
        this.grants = grants;
    }

    public void onCallback(Ctx ctx, String[] p) {
        if (p.length < 3) {
            ctx.send(Texts.STALE);
            return;
        }
        long id = Long.parseLong(p[1]);
        Decision d = switch (p[2]) {
            case "a" -> Decision.APPROVE;
            case "d" -> Decision.DECLINE;
            default -> Decision.REVOKE;
        };
        Map.Entry<Grant, Boolean> r = grants.decide(ctx.businessId(), id, d);
        Grant g = r.getKey();
        ctx.clearSourceButtons();
        if (!r.getValue()) {
            Status st = grants.statusOf(g);
            ctx.send("ℹ️ Bu so‘rov allaqachon yakunlangan (holat: " + statusLabel(st) + ").");
            return;
        }
        switch (d) {
            case APPROVE -> ctx.send("✅ Ruxsat berildi: faqat ko‘rish, <b>" + TIME.format(g.getExpiresAt())
                            + "</b> gacha. Istalgan payt bekor qilishingiz mumkin.",
                    Kb.create().row(Kb.cb("🚫 Ruxsatni bekor qilish", "g:" + id + ":r")));
            case DECLINE -> ctx.send("❌ So‘rov rad etildi. Ma’lumotlaringiz ko‘rsatilmaydi.");
            case REVOKE -> ctx.send("🚫 Ruxsat bekor qilindi. Administrator endi ma’lumotlaringizni ko‘ra olmaydi.");
        }
    }

    private static String statusLabel(Status s) {
        return switch (s) {
            case REQUESTED -> "kutilmoqda";
            case APPROVED -> "ruxsat berilgan";
            case DECLINED -> "rad etilgan";
            case REVOKED -> "bekor qilingan";
            case EXPIRED -> "muddati tugagan";
        };
    }
}
