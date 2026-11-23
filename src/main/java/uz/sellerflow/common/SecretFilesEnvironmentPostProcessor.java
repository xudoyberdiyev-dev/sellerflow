package uz.sellerflow.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sirlarni fayldan o'qish (Docker secrets): {@code NAME_FILE=/yo'l} yoki {@code /run/secrets/NAME} mavjud bo'lsa va
 * {@code NAME} muhit o'zgaruvchisi bo'sh bo'lsa — qiymat fayldan olinadi. Shunda sir {@code docker inspect} da ko'rinmaydi.
 * Fayl tarkibi logga yozilmaydi; oxiridagi qator oxiri olib tashlanadi.
 */
public class SecretFilesEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final List<String> NAMES = List.of("TELEGRAM_BOT_TOKEN", "DB_PASSWORD", "ADMIN_BOOTSTRAP_PASSWORD",
            "ADMIN_RECOVERY_PASSWORD", "SELLERFLOW_SECRETS_KEY");
    static Path secretsDir = Path.of("/run/secrets");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Map<String, Object> found = new HashMap<>();
        for (String name : NAMES) {
            String current = env.getProperty(name);
            if (current != null && !current.isBlank()) continue;
            String fileVar = env.getProperty(name + "_FILE");
            Path p = fileVar != null && !fileVar.isBlank() ? Path.of(fileVar.strip()) : secretsDir.resolve(name);
            if (!Files.isRegularFile(p)) {
                if (fileVar != null && !fileVar.isBlank()) throw new IllegalStateException(name + "_FILE faylini o'qib bo'lmadi");
                continue;
            }
            try {
                String v = Files.readString(p, StandardCharsets.UTF_8).replaceAll("[\\r\\n]+$", "");
                if (!v.isEmpty()) found.put(name, v);
            } catch (IOException e) {
                throw new IllegalStateException(name + " sir faylini o'qib bo'lmadi");
            }
        }
        if (!found.isEmpty()) env.getPropertySources().addFirst(new MapPropertySource("sellerflowSecretFiles", found));
        // Production (docker-compose): token majburiy — muhitda ham, sir faylida ham bo'lmasa ishga tushmaydi
        if ("true".equalsIgnoreCase(env.getProperty("TELEGRAM_REQUIRED"))) {
            String t = env.getProperty("TELEGRAM_BOT_TOKEN");
            if (t == null || t.isBlank()) {
                throw new IllegalStateException("TELEGRAM_BOT_TOKEN .env faylida berilishi shart (yoki /run/secrets/TELEGRAM_BOT_TOKEN)");
            }
        }
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
