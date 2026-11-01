package com.nexadesk.user.password;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** At least 10 characters with an uppercase letter, a lowercase letter, a digit and a special character. */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Le mot de passe doit contenir au moins 10 caractères, dont une majuscule, "
            + "une minuscule, un chiffre et un caractère spécial.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
