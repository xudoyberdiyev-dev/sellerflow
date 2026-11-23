package uz.sellerflow.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretFilesEnvironmentPostProcessorTest {

    @TempDir Path dir;
    final Path original = SecretFilesEnvironmentPostProcessor.secretsDir;

    @AfterEach
    void restore() { SecretFilesEnvironmentPostProcessor.secretsDir = original; }

    @Test
    void readsFileVar_andRunSecrets_onlyWhenEnvBlank() throws Exception {
        SecretFilesEnvironmentPostProcessor.secretsDir = dir;
        Files.writeString(dir.resolve("DB_PASSWORD"), "db-secret\n");
        Path tok = dir.resolve("tok.txt");
        Files.writeString(tok, "123:ABC\r\n");
        MockEnvironment env = new MockEnvironment()
                .withProperty("TELEGRAM_BOT_TOKEN", "")
                .withProperty("TELEGRAM_BOT_TOKEN_FILE", tok.toString())
                .withProperty("SELLERFLOW_SECRETS_KEY", "from-env");
        Files.writeString(dir.resolve("SELLERFLOW_SECRETS_KEY"), "from-file");
        new SecretFilesEnvironmentPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("TELEGRAM_BOT_TOKEN")).isEqualTo("123:ABC");
        assertThat(env.getProperty("DB_PASSWORD")).isEqualTo("db-secret");
        assertThat(env.getProperty("SELLERFLOW_SECRETS_KEY")).as("muhit qiymati ustun").isEqualTo("from-env");
    }

    @Test
    void missingExplicitFile_failsFast() {
        SecretFilesEnvironmentPostProcessor.secretsDir = dir;
        MockEnvironment env = new MockEnvironment().withProperty("DB_PASSWORD_FILE", dir.resolve("nope").toString());
        assertThatThrownBy(() -> new SecretFilesEnvironmentPostProcessor().postProcessEnvironment(env, null))
                .hasMessageContaining("DB_PASSWORD_FILE");
    }

    @Test
    void telegramRequired_withoutTokenAnywhere_failsFast() {
        SecretFilesEnvironmentPostProcessor.secretsDir = dir;
        MockEnvironment env = new MockEnvironment().withProperty("TELEGRAM_REQUIRED", "true").withProperty("TELEGRAM_BOT_TOKEN", "");
        assertThatThrownBy(() -> new SecretFilesEnvironmentPostProcessor().postProcessEnvironment(env, null))
                .hasMessageContaining("TELEGRAM_BOT_TOKEN .env faylida berilishi shart");
    }
}
