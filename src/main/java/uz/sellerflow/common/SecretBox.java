package uz.sellerflow.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sotuvchi sirlarini (marketplace API kalitlari) shifrlash: AES-256-GCM. Kalit faqat muhitdan
 * ({@code SELLERFLOW_SECRETS_KEY}, base64, 32 bayt) — bazada ham, backup'da ham yo'q.
 * Format: [1 bayt versiya][12 bayt IV][shifrmatn + 16 bayt tag]. AAD — kimga tegishliligi (boshqa biznesga ko'chirilsa ochilmaydi).
 */
@Component
public class SecretBox {

    private static final byte VERSION = 1;
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretBox(@Value("${sellerflow.secrets.key:}") String base64Key) {
        SecretKeySpec k = null;
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(base64Key.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("SELLERFLOW_SECRETS_KEY base64 emas (openssl rand -base64 32)");
            }
            if (raw.length != 32) throw new IllegalStateException("SELLERFLOW_SECRETS_KEY 32 bayt bo'lishi shart (openssl rand -base64 32)");
            k = new SecretKeySpec(raw, "AES");
        }
        this.key = k;
    }

    public boolean enabled() { return key != null; }

    public byte[] encrypt(String plaintext, String aad) {
        requireKey();
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_LEN + ct.length).put(VERSION).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(byte[] blob, String aad) {
        requireKey();
        if (blob == null || blob.length < 1 + IV_LEN + 16 || blob[0] != VERSION) throw new IllegalStateException("bad secret blob");
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 1, IV_LEN));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] pt = c.doFinal(blob, 1 + IV_LEN, blob.length - 1 - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("decrypt failed (kalit almashganmi?)");
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("SELLERFLOW_SECRETS_KEY berilmagan");
    }
}
