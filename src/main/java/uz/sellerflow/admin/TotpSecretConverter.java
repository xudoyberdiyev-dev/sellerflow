package uz.sellerflow.admin;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import uz.sellerflow.common.SecretBox;

import java.util.Base64;

/**
 * Admin TOTP sirini bazada shifrlangan holda saqlash: {@code enc:v1:<base64(SecretBox blob)>}.
 * {@code SELLERFLOW_SECRETS_KEY} bo'lmasa — eski usulda ochiq (ogohlantirish bilan, {@link TotpSecretMigrator}).
 * Prefikssiz qiymat — eski (ochiq) yozuv sifatida o'qiladi va startda shifrlanadi.
 */
@Component
@Converter
public class TotpSecretConverter implements AttributeConverter<String, String> {

    static final String PREFIX = "enc:v1:";
    /**
     * Kalit almashgan/yo'qolgan: sir o'qilmaydi. Login xavfsiz rad etiladi (kod hech qachon mos kelmaydi),
     * 2FA qayta sozlashga O'TMAYDI, bazadagi shifrmatn o'zgarmaydi (to'g'ri kalit qaytarilsa yana ishlaydi).
     * Tiklash: to'g'ri kalitni qaytarish yoki ADMIN_RECOVERY_* (2FA reset).
     */
    public static final String UNREADABLE = "!unreadable:";
    static final String AAD = "admin-totp";

    private final SecretBox box;

    public TotpSecretConverter(SecretBox box) {
        this.box = box;
    }

    @Override
    public String convertToDatabaseColumn(String secret) {
        if (secret != null && secret.startsWith(UNREADABLE)) return secret.substring(UNREADABLE.length());
        if (secret == null || !box.enabled() || secret.startsWith(PREFIX)) return secret;
        return PREFIX + Base64.getEncoder().encodeToString(box.encrypt(secret, AAD));
    }

    @Override
    public String convertToEntityAttribute(String db) {
        if (db == null || !db.startsWith(PREFIX)) return db;
        try {
            return box.decrypt(Base64.getDecoder().decode(db.substring(PREFIX.length())), AAD);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(TotpSecretConverter.class)
                    .error("Admin TOTP sirini ochib bo'lmadi — SELLERFLOW_SECRETS_KEY almashganmi? Tiklash: docs/PRODUCTION_SECURITY.md");
            return UNREADABLE + db;
        }
    }
}
