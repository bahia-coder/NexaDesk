package com.nexadesk.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deployment settings the web client needs before anyone signs in. Public: nothing here is sensitive.
 */
@RestController
@RequestMapping("/api/client-config")
@Tag(name = "Configuration", description = "Réglages de déploiement lus par l'interface au démarrage")
public class ClientConfigController {

    /** {@code timeZone}: IANA zone of the company (APP_TIMEZONE); every date is displayed in it. */
    public record ClientConfigDto(String timeZone) {
    }

    private final ClientConfigDto config;

    public ClientConfigController(@Value("${nexadesk.time-zone}") ZoneId zone) {
        this.config = new ClientConfigDto(zone.getId());
    }

    @GetMapping
    @Operation(summary = "Réglages de l'interface", description = "Fuseau horaire de l'entreprise (APP_TIMEZONE).")
    public ClientConfigDto get() {
        return config;
    }
}
