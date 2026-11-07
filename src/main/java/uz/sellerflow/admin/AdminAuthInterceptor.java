package uz.sellerflow.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * /admin/api/** uchun autentifikatsiya (H-1). Xom URI emas, Spring MVC'ning o'zi handler tanlashda
 * ishlatadigan dekodlangan va normallashtirilgan yo'l bo'yicha ishlaydi (InterceptorRegistry path pattern),
 * shuning uchun "/admin/%61pi/..." kabi yo'llar ham shu tekshiruvdan o'tadi.
 * Deny-by-default: {@link PublicAdminEndpoint} bilan belgilanmagan har qanday handler (jumladan mavjud bo'lmagan
 * yo'llar uchun static resurs handleri) Bearer sessiya talab qiladi.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAuthService auth;

    public AdminAuthInterceptor(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        res.setHeader("Cache-Control", "no-store");
        if (handler instanceof HandlerMethod hm && hm.hasMethodAnnotation(PublicAdminEndpoint.class)) {
            return true;
        }
        var principal = auth.authenticate(AdminController.bearer(req));
        if (principal.isEmpty()) {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
            return false;
        }
        req.setAttribute(AdminController.PRINCIPAL, principal.get());
        return true;
    }
}
