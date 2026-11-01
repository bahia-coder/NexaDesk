package com.nexadesk.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} property of every error response.
 * The frontend switches on the code; the message is a French default shown to the user.
 */
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Certaines données sont invalides."),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "Ce critère de tri n'est pas autorisé."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentification requise."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Votre session a expiré. Veuillez vous reconnecter."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Vous n'avez pas les droits nécessaires pour cette action."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Ce compte est désactivé. Contactez votre administrateur."),
    ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Origine de la requête non autorisée."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Vous devez changer votre mot de passe temporaire avant de continuer."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "La ressource demandée est introuvable."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Cet élément a été modifié par quelqu'un d'autre. Rechargez la page."),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée par un autre compte."),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "Ce nom est déjà utilisé."),
    ACTION_NOT_ALLOWED(HttpStatus.CONFLICT, "Cette action n'est pas possible sur ce ticket dans son état actuel."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Cette catégorie est utilisée par des tickets en cours."),
    TECHNICIAN_HAS_OPEN_TICKETS(HttpStatus.CONFLICT,
            "Ce technicien a encore des tickets en cours : réaffectez-les d'abord."),
    INVALID_ASSIGNEE(HttpStatus.UNPROCESSABLE_ENTITY, "Un ticket ne peut être affecté qu'à un technicien actif."),
    ATTACHMENT_LIMIT(HttpStatus.UNPROCESSABLE_ENTITY, "Nombre maximal de pièces jointes atteint pour ce ticket."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Type de fichier non autorisé (PNG, JPG, PDF, TXT, LOG, DOCX, XLSX)."),
    INACTIVE_REFERENCE(HttpStatus.UNPROCESSABLE_ENTITY, "Cet élément est désactivé et ne peut pas être utilisé."),
    SPECIALTIES_TECHNICIAN_ONLY(HttpStatus.UNPROCESSABLE_ENTITY,
            "Seuls les techniciens peuvent avoir des spécialités."),
    SELF_MODIFICATION_FORBIDDEN(HttpStatus.UNPROCESSABLE_ENTITY,
            "Vous ne pouvez pas modifier votre propre rôle ni désactiver votre propre compte."),
    LAST_ADMIN(HttpStatus.UNPROCESSABLE_ENTITY, "L'application doit conserver au moins un administrateur actif."),
    INVALID_CURRENT_PASSWORD(HttpStatus.UNPROCESSABLE_ENTITY, "Le mot de passe actuel est incorrect."),
    PASSWORD_UNCHANGED(HttpStatus.UNPROCESSABLE_ENTITY, "Le nouveau mot de passe doit être différent de l'actuel."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Compte temporairement verrouillé après plusieurs échecs de connexion."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue est survenue.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
