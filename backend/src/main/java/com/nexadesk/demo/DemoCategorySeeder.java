package com.nexadesk.demo;

import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only: 8 categories / 26 subcategories with classifier keywords, and the specialties of the
 * 5 demo technicians. Runs after {@link DemoUserSeeder}; idempotent (skips if categories already exist).
 */
@Slf4j
@Component
@Profile("demo")
@Order(2)
@RequiredArgsConstructor
public class DemoCategorySeeder implements ApplicationRunner {

    private record Category(String name, String icon, String description, String keywords,
                            List<String> subcategories) {
    }

    static final String NETWORK = "Réseau & VPN";
    static final String HARDWARE = "Matériel";
    static final String PRINTING = "Impression";
    static final String BUSINESS_APPS = "Logiciels métier";
    static final String WORKSTATION = "Poste de travail";
    static final String ACCOUNTS = "Comptes & accès";
    static final String MESSAGING = "Messagerie & collaboration";
    static final String SECURITY = "Sécurité";

    private static final List<Category> CATEGORIES = List.of(
            new Category(NETWORK, "wifi", "Connexion réseau, Wi-Fi, VPN et accès internet",
                    "réseau, wifi, wi-fi, vpn, internet, connexion, câble, prise, déconnecté, lent",
                    List.of("Wi-Fi", "VPN", "Accès internet", "Câblage / prise réseau")),
            new Category(HARDWARE, "monitor", "Ordinateurs, écrans et périphériques",
                    "ordinateur, portable, pc, écran, clavier, souris, batterie, chargeur, allume, casse",
                    List.of("Ordinateur portable", "Poste fixe", "Écran", "Périphériques (clavier, souris)")),
            new Category(PRINTING, "printer", "Imprimantes, scanners et consommables",
                    "imprimante, impression, imprimer, scanner, toner, cartouche, papier, bourrage",
                    List.of("Imprimante", "Scanner", "Consommables")),
            new Category(BUSINESS_APPS, "app-window", "Applications métier et installation de logiciels",
                    "logiciel, application, erp, comptabilité, crm, licence, installer, installation, bug",
                    List.of("ERP / Comptabilité", "CRM", "Installation de logiciel")),
            new Category(WORKSTATION, "laptop", "Système d'exploitation, performances et mises à jour",
                    "windows, système, lent, lenteur, plantage, redémarrage, mise à jour, écran bleu, disque",
                    List.of("Système d'exploitation", "Lenteurs / performances", "Mises à jour")),
            new Category(ACCOUNTS, "key-round", "Mots de passe, comptes et droits d'accès",
                    "mot de passe, compte, accès, droits, autorisation, verrouillé, bloqué, partage, dossier",
                    List.of("Mot de passe", "Création de compte", "Droits d'accès / partages")),
            new Category(MESSAGING, "mail", "Messagerie, agenda et visioconférence",
                    "mail, e-mail, messagerie, outlook, boîte, calendrier, agenda, réunion, teams, visio",
                    List.of("Messagerie", "Visioconférence", "Calendrier")),
            new Category(SECURITY, "shield-alert", "Incidents de sécurité, hameçonnage, perte ou vol",
                    "virus, phishing, hameçonnage, suspect, piratage, malveillant, vol, perte, rançon",
                    List.of("Hameçonnage / e-mail suspect", "Virus / logiciel malveillant",
                            "Perte ou vol de matériel")));

    private static final Map<String, List<String>> SPECIALTIES = Map.of(
            DemoAccounts.TECHNICIAN_EMAIL, List.of(NETWORK, SECURITY),
            "tech.materiel@nexadesk.test", List.of(HARDWARE, PRINTING),
            "tech.logiciel@nexadesk.test", List.of(BUSINESS_APPS, WORKSTATION),
            "tech.acces@nexadesk.test", List.of(ACCOUNTS, SECURITY),
            "tech.messagerie@nexadesk.test", List.of(MESSAGING, WORKSTATION));

    private final TicketCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            log.info("Categories already present, skipping demo categories");
            return;
        }

        Map<String, TicketCategory> byName = new LinkedHashMap<>();
        int subcategoryCount = 0;
        for (Category c : CATEGORIES) {
            TicketCategory category = new TicketCategory(c.name(), c.description(), c.icon(), c.keywords());
            c.subcategories().forEach(category::addSubcategory);
            subcategoryCount += c.subcategories().size();
            byName.put(c.name(), categoryRepository.save(category)); // subcategories cascade on persist
        }

        SPECIALTIES.forEach((email, categoryNames) -> userRepository.findByEmail(email).ifPresent(technician ->
                technician.replaceSpecialties(categoryNames.stream().map(byName::get).toList())));

        log.info("Demo categories created: {} categories, {} subcategories", CATEGORIES.size(), subcategoryCount);
    }
}
