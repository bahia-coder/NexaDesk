package com.nexadesk.user.dto;

/** Validation rules shared by the admin and profile requests. */
public final class UserFieldRules {

    public static final String PHONE_REGEX = "^[+0-9 ().-]{6,30}$";
    public static final String PHONE_MESSAGE = "Le numéro de téléphone n'est pas valide.";
    public static final String COLOR_REGEX = "^#[0-9A-Fa-f]{6}$";
    public static final String COLOR_MESSAGE = "La couleur doit être au format #RRGGBB.";

    private UserFieldRules() {
    }
}
