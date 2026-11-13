package uz.sellerflow.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.sellerflow.common.SellerFlowProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * LOW (maxfiylik): TERMS_VERSION shartlar matnining SHA-256 xeshiga bog'lanadi. Bir xil versiya bilan matn
 * o'zgartirilsa — ilova ishga tushmaydi (foydalanuvchilar eski versiyaga bergan rozilik bilan yangi matnni
 * «qabul qilgan» bo'lib qolmasin). Matnni o'zgartirganda TERMS_VERSION ni ham oshiring — qayta rozilik so'raladi.
 */
@Component
public class TermsGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TermsGuard.class);

    private final JdbcTemplate jdbc;
    private final SellerFlowProperties props;

    public TermsGuard(JdbcTemplate jdbc, SellerFlowProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String text = new String(new ClassPathResource("bot/terms_uz.txt").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).strip();
        verify(props.terms().version(), text);
    }

    public void verify(String version, String text) {
        String hash = sha256(normalize(text));
        jdbc.update("insert into terms_versions(version, text_sha256) values (?, ?) on conflict do nothing", version, hash);
        String stored = jdbc.queryForObject("select text_sha256 from terms_versions where version = ?", String.class, version);
        if (!hash.equals(stored)) {
            throw new IllegalStateException("terms_uz.txt changed but TERMS_VERSION '" + version
                    + "' did not — bump TERMS_VERSION so users re-consent to the new text");
        }
        log.info("Terms version {} (sha256 {}…)", version, hash.substring(0, 12));
    }

    /**
     * N-1: xesh qator oxirlariga bog'liq bo'lmasin — Windows'da {@code core.autocrlf=true} bilan clone qilingan
     * repoda fayl CRLF bo'lib qoladi va image ishga tushmay qolardi. CRLF/CR → LF, chetdagi bo'shliqlar olib tashlanadi.
     */
    public static String normalize(String text) {
        String t = text.startsWith("\uFEFF") ? text.substring(1) : text; // Windows muharrirlari qo'shadigan BOM
        return t.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
