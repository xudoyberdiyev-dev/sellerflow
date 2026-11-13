package uz.sellerflow.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Spring Security StrictHttpFirewall'ga o'xshash minimal himoya (H-1): yo'l-parametrlari (";"),
 * kodlangan ajratgichlar ("%2f", "%5c", "%2e", "%25", "%3b"), teskari chiziq, "//" va "." / ".." segmentlari
 * bo'lgan so'rovlar 400 bilan rad etiladi — xom URI va Spring MVC ko'radigan yo'l bir-biridan farq qilmasin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestFirewall extends OncePerRequestFilter {

    private static final String[] FORBIDDEN = {";", "\\", "//", "%2f", "%5c", "%2e", "%25", "%3b", "%00"};

    static final long MAX_BODY_BYTES = 64 * 1024;

    private static void reject(HttpServletResponse res, int status, String code) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + code + "\"}");
    }

    static boolean isRejected(String rawUri) {
        if (rawUri == null || rawUri.isEmpty() || rawUri.charAt(0) != '/') return true;
        String u = rawUri.toLowerCase(Locale.ROOT);
        for (String f : FORBIDDEN) if (u.contains(f)) return true;
        for (String seg : u.split("/", -1)) if (seg.equals(".") || seg.equals("..")) return true;
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c < 0x21 || c == 0x7f) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (isRejected(req.getRequestURI())) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"BAD_REQUEST_PATH\"}");
            return;
        }
        // LOW: admin API so'rov tanasi hajmi cheklangan (autentifikatsiyasiz login endpointlari ham) — xotira DoS yo'q
        if (req.getRequestURI().startsWith("/admin/api/")) {
            long len = req.getContentLengthLong();
            if (len > MAX_BODY_BYTES) {
                reject(res, 413, "PAYLOAD_TOO_LARGE");
                return;
            }
            if (len < 0 && req.getHeader("Transfer-Encoding") != null) {
                reject(res, 411, "LENGTH_REQUIRED");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
