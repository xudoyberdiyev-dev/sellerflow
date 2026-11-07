package uz.sellerflow.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/** Superadmin SPA: /admin/ -> static/admin/index.html (Docker build vaqtida admin-ui/dist dan ko'chiriladi). */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuth;

    public AdminWebConfig(AdminAuthInterceptor adminAuth) {
        this.adminAuth = adminAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Spring MVC handler tanlashda ishlatadigan yo'l bo'yicha moslanadi (xom URI emas) — H-1.
        registry.addInterceptor(adminAuth).addPathPatterns("/admin/api", "/admin/api/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }

    /** Admin sahifalari uchun xavfsizlik sarlavhalari. */
    @Component
    public static class AdminSecurityHeaders extends OncePerRequestFilter {
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !request.getServletPath().startsWith("/admin");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            if (req.isSecure()) {
                // HTTPS (reverse proxy X-Forwarded-Proto orqali ham) — brauzer keyin faqat HTTPS ishlatsin
                res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
            String path = req.getServletPath();
            if (path.equals("/admin") || path.equals("/admin/") || path.equals("/admin/index.html")) {
                // LOW (UI): yangi deploydan keyin eski index.html (eski asset hash'lari bilan) keshdan olinmasin
                res.setHeader("Cache-Control", "no-cache");
            } else if (path.startsWith("/admin/assets/")) {
                res.setHeader("Cache-Control", "public, max-age=31536000, immutable"); // fayl nomida hash bor
            }
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("Referrer-Policy", "no-referrer");
            res.setHeader("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'");
            chain.doFilter(req, res);
        }
    }
}
