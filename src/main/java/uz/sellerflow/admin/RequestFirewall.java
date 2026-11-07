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
        chain.doFilter(req, res);
    }
}
