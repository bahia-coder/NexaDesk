package com.nexadesk.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only exists with the {@code demo} profile: elsewhere the route is simply not mapped. */
@Profile("demo")
@RestController
@Tag(name = "Authentification")
public class DemoAccountsController {

    @GetMapping("/api/auth/demo-accounts")
    @SecurityRequirements
    @Operation(summary = "Comptes de démonstration (profil demo uniquement)")
    public List<DemoAccounts.DemoAccount> demoAccounts() {
        return DemoAccounts.SHOWCASE;
    }
}
