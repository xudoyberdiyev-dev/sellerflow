package uz.sellerflow.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import uz.sellerflow.account.AccountEntities.SalesChannel;
import uz.sellerflow.calculation.CalcTypes.ExpenseCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * V1+ (TZ 2-modul): sotuv kanali bo'yicha saqlangan xarajat stavkalari. Ma'lumot har doim {@code business_id} bo'yicha.
 * Stavkalar bot qoralamasidagi format ({@code {code:{s,t,v}}}) bilan saqlanadi; chegirma kanal stavkasi emas — saqlanmaydi.
 */
@Service
public class ChannelRateService {

    public record ChannelRates(SalesChannel channel, ObjectNode expenses) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ChannelRateService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<ObjectNode> get(long businessId, SalesChannel channel) {
        List<ObjectNode> r = jdbc.query("select expenses::text from business_channels where business_id = ? and channel = ?",
                (rs, i) -> parse(rs.getString(1)), businessId, channel.name());
        return r.stream().findFirst();
    }

    public List<ChannelRates> list(long businessId) {
        List<ChannelRates> out = new ArrayList<>();
        jdbc.query("select channel, expenses::text from business_channels where business_id = ? order by channel",
                rs -> {
                    try {
                        out.add(new ChannelRates(SalesChannel.valueOf(rs.getString(1)), parse(rs.getString(2))));
                    } catch (IllegalArgumentException ignored) {
                        // noma'lum kanal kodi — o'tkazib yuboriladi
                    }
                }, businessId);
        return out;
    }

    /** Qoralama xarajatlaridan stavkalarni saqlaydi (chegirmasiz, faqat kiritilganlari). */
    public int save(long businessId, SalesChannel channel, JsonNode draftExpenses) {
        ObjectNode clean = sanitize(draftExpenses);
        if (clean.isEmpty()) return 0;
        jdbc.update("""
                insert into business_channels(business_id, channel, expenses) values (?, ?, ?::jsonb)
                on conflict (business_id, channel) do update set expenses = excluded.expenses, updated_at = now()""",
                businessId, channel.name(), clean.toString());
        return clean.size();
    }

    public boolean delete(long businessId, SalesChannel channel) {
        return jdbc.update("delete from business_channels where business_id = ? and channel = ?", businessId, channel.name()) > 0;
    }

    ObjectNode sanitize(JsonNode draft) {
        ObjectNode out = mapper.createObjectNode();
        if (draft == null || !draft.isObject()) return out;
        for (ExpenseCategory c : ExpenseCategory.values()) {
            if (c == ExpenseCategory.DISCOUNT) continue;
            JsonNode n = draft.get(c.code());
            if (n == null || !n.isObject()) continue;
            String s = n.path("s").asText("");
            if ("ZERO".equals(s) || "UNKNOWN".equals(s)) {
                out.putObject(c.code()).put("s", s);
            } else if ("KNOWN".equals(s)) {
                String t = n.path("t").asText("");
                String v = n.path("v").asText("");
                if (!("PCT".equals(t) || "FIX".equals(t)) || v.isEmpty()) continue;
                if ("PCT".equals(t) && !c.allowsPercent()) continue;
                try {
                    new java.math.BigDecimal(v);
                } catch (NumberFormatException e) {
                    continue;
                }
                out.putObject(c.code()).put("s", s).put("t", t).put("v", v);
            }
        }
        return out;
    }

    private ObjectNode parse(String json) {
        try {
            JsonNode n = mapper.readTree(json);
            return n instanceof ObjectNode o ? sanitize(o) : mapper.createObjectNode();
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
