package uz.sellerflow.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;

/**
 * M-13: /actuator/** (health'dan tashqari, masalan prometheus) faqat loopback yoki ichki (private) tarmoqdan.
 * Reverse proxy ortida RemoteIpValve haqiqiy mijoz IP'sini beradi — internetdan kelgan so'rov 404 oladi.
 */
@Component
public class ActuatorAccessFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getServletPath();
        return !p.startsWith("/actuator") || p.equals("/actuator/health") || p.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!isInternal(req.getRemoteAddr())) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(req, res);
    }

    static boolean isInternal(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+")) return false; // DNS so'rovi qilinmasin
        try {
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress()
                    || (a.getAddress().length == 16 && (a.getAddress()[0] & 0xfe) == 0xfc); // IPv6 ULA fc00::/7
        } catch (Exception e) {
            return false;
        }
    }
}
