package uz.sellerflow.admin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * M-5: vaqtinchalik parol bilan kirgan (mustChangePassword) admin faqat shu belgili endpointlarni
 * chaqira oladi (/auth/me, /auth/password, /auth/logout). Qolganlari — 403 PASSWORD_CHANGE_REQUIRED.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedWithTempPassword {}
