package uz.sellerflow.admin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * /admin/api/** ostida sessiyasiz ochiq bo'lgan endpoint (faqat login va TOTP tasdiqlash).
 * Belgilanmagan har qanday handler Bearer sessiya talab qiladi (deny-by-default, H-1).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicAdminEndpoint {}
