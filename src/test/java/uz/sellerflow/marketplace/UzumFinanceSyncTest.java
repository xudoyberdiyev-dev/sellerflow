package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UzumFinanceSyncTest {

    @Test
    void classify_byCode_cyrillicLatin_thenByName() {
        assertThat(UzumFinanceSync.classify("С000003", null)).isEqualTo("STORAGE");   // Kiril С
        assertThat(UzumFinanceSync.classify("C000004", null)).isEqualTo("RETURN_STORAGE");
        assertThat(UzumFinanceSync.classify("У000119", null)).isEqualTo("ADS");       // Kiril У
        assertThat(UzumFinanceSync.classify("FBS004", null)).isEqualTo("FINE");
        assertThat(UzumFinanceSync.classify("BALANCE_CORRECTION", null)).isEqualTo("TRANSFER");
        assertThat(UzumFinanceSync.classify("NEW1", "Штраф за опоздание")).isEqualTo("FINE");
        assertThat(UzumFinanceSync.classify(null, "Хранение возврата")).isEqualTo("RETURN_STORAGE");
        assertThat(UzumFinanceSync.classify("X", "Nimadir")).isEqualTo("OTHER");
    }

    @Test
    void instant_parsesMillisSecondsIsoAndLocal() throws Exception {
        var m = new ObjectMapper();
        assertThat(UzumFinanceSync.instant(m.readTree("{\"date\":1743465600000}"), "date")).isEqualTo(Instant.ofEpochMilli(1743465600000L));
        assertThat(UzumFinanceSync.instant(m.readTree("{\"date\":1743465600}"), "date")).isEqualTo(Instant.ofEpochSecond(1743465600L));
        assertThat(UzumFinanceSync.instant(m.readTree("{\"date\":\"2026-11-01T10:00:00Z\"}"), "date")).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(UzumFinanceSync.instant(m.readTree("{\"x\":\"2026-11-01T15:00:00\"}"), "date", "x")).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(UzumFinanceSync.instant(m.readTree("{\"date\":\"bad\"}"), "date")).isNull();
    }
}
