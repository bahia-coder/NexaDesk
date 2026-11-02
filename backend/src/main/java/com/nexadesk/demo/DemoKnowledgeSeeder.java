package com.nexadesk.demo;

import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.knowledge.ArticleStatus;
import com.nexadesk.knowledge.KnowledgeArticle;
import com.nexadesk.knowledge.KnowledgeArticleRepository;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only: 12 knowledge articles written by the demo technicians (10 published, 1 draft, 1 archived),
 * with views, "helpful" votes from employees, and links to the solved demo tickets of the same subcategory.
 * Dates are relative to startup. Idempotent (skipped if any article exists).
 */
@Slf4j
@Component
@Profile("demo")
@Order(7)
@RequiredArgsConstructor
public class DemoKnowledgeSeeder implements ApplicationRunner {

    private record Article(String category, String subcategory, String author, ArticleStatus status, int ageDays,
                           int views, int helpful, String title, String summary, String content) {
    }

    private static final String NETWORK_TECH = DemoAccounts.TECHNICIAN_EMAIL;
    private static final String HARDWARE_TECH = "tech.materiel@nexadesk.test";
    private static final String SOFTWARE_TECH = "tech.logiciel@nexadesk.test";
    private static final String ACCESS_TECH = "tech.acces@nexadesk.test";
    private static final String MESSAGING_TECH = "tech.messagerie@nexadesk.test";

    private static final List<Article> ARTICLES = List.of(
            new Article(DemoCategorySeeder.NETWORK, "VPN", NETWORK_TECH, ArticleStatus.PUBLISHED, 150, 214, 11,
                    "Résoudre l'erreur VPN 809 en télétravail",
                    "La connexion VPN échoue avec l'erreur 809 : vérifications à faire chez soi avant d'appeler "
                            + "le support.",
                    """
                    ## Symptômes

                    Le client VPN affiche « Erreur 809 : la connexion réseau entre votre ordinateur et le serveur \
                    VPN n'a pas pu être établie ». Les dossiers partagés et l'ERP sont inaccessibles.

                    ## Vérifications

                    1. Vérifiez que vous avez accès à internet (ouvrez un site public).
                    2. Redémarrez votre box : certaines box bloquent le VPN après plusieurs jours sans redémarrage.
                    3. Si vous utilisez le partage de connexion d'un téléphone, essayez depuis une autre connexion : \
                    certains opérateurs mobiles filtrent le VPN.
                    4. Fermez complètement le client VPN (icône près de l'horloge, « Quitter »), puis relancez-le.

                    ## Si l'erreur persiste

                    Ouvrez un ticket « Réseau & VPN » en précisant l'heure de l'erreur et le type de connexion \
                    (box, 4G). Une erreur 809 touchant plusieurs collègues en même temps vient souvent du \
                    certificat de la passerelle : le support la traite en priorité."""),
            new Article(DemoCategorySeeder.NETWORK, "Wi-Fi", NETWORK_TECH, ArticleStatus.PUBLISHED, 120, 98, 4,
                    "Wi-Fi instable en salle de réunion : que faire ?",
                    "Coupures Wi-Fi pendant les réunions : les bons réflexes et les informations utiles au "
                            + "support.",
                    """
                    ## Symptômes

                    La connexion Wi-Fi se coupe régulièrement, surtout quand la salle est pleine.

                    ## Que faire tout de suite

                    1. Connectez-vous au réseau « NexaCorp » et non au réseau invité, limité en débit.
                    2. Désactivez puis réactivez le Wi-Fi de votre ordinateur.
                    3. Pour une visioconférence importante, branchez-vous sur la prise réseau de la table.

                    ## Informations à donner au support

                    Le nom de la salle, les heures des coupures et le nombre approximatif de participants. Ces \
                    informations permettent de vérifier la charge de la borne et son canal."""),
            new Article(DemoCategorySeeder.HARDWARE, "Ordinateur portable", HARDWARE_TECH, ArticleStatus.PUBLISHED,
                    140, 176, 9,
                    "Mon ordinateur portable ne s'allume plus",
                    "Le portable reste éteint même branché : réinitialisation de l'alimentation et vérification "
                            + "du chargeur.",
                    """
                    ## Symptômes

                    L'ordinateur ne démarre pas, le voyant de charge clignote ou reste éteint.

                    ## Étapes

                    1. Débranchez le chargeur et tous les périphériques (station d'accueil, clés USB).
                    2. Maintenez le bouton d'alimentation appuyé 20 secondes : cela décharge l'électricité \
                    résiduelle.
                    3. Rebranchez uniquement le chargeur, directement sur une prise murale, attendez 5 minutes puis \
                    rallumez.
                    4. Si possible, essayez le chargeur d'un collègue du même modèle.

                    ## Si rien ne change

                    Ouvrez un ticket « Matériel ». Si le chargeur d'un collègue fonctionne, précisez-le : le \
                    support vous remettra un chargeur neuf sans immobiliser votre poste."""),
            new Article(DemoCategorySeeder.PRINTING, "Imprimante", HARDWARE_TECH, ArticleStatus.PUBLISHED, 110, 143, 8,
                    "Bourrage papier sur les imprimantes d'étage",
                    "Retirer un bourrage sans abîmer l'imprimante, et éviter qu'il se reproduise en recto verso.",
                    """
                    ## Retirer le papier coincé

                    1. Ouvrez le capot indiqué sur l'écran de l'imprimante (souvent le capot latéral droit).
                    2. Tirez la feuille doucement, dans le sens du défilement, sans la déchirer.
                    3. Vérifiez le bac recto verso : un petit morceau de papier suffit à provoquer de nouveaux \
                    bourrages.
                    4. Refermez tous les capots : l'imprimante reprend l'impression.

                    ## Éviter les bourrages répétés

                    - Aérez la ramette avant de la mettre dans le bac et ne dépassez pas le repère de remplissage.
                    - N'utilisez pas de papier humide ou déjà imprimé d'un côté.

                    Si les bourrages se répètent en recto verso, ouvrez un ticket « Impression » : un rouleau \
                    d'entraînement usé doit être remplacé par le support."""),
            new Article(DemoCategorySeeder.PRINTING, "Consommables", HARDWARE_TECH, ArticleStatus.ARCHIVED, 170, 61, 1,
                    "Commander du toner avec le formulaire papier",
                    "Ancienne procédure de commande de consommables, remplacée par les tickets de demande.",
                    """
                    Cette procédure n'est plus utilisée : les consommables se demandent désormais par un ticket \
                    « Impression > Consommables ».

                    Ancienne procédure : remplir le formulaire de commande, le faire signer par le responsable \
                    de service et le déposer au service informatique."""),
            new Article(DemoCategorySeeder.BUSINESS_APPS, "ERP / Comptabilité", SOFTWARE_TECH, ArticleStatus.PUBLISHED,
                    100, 87, 5,
                    "ERP : « écriture verrouillée » pendant la clôture",
                    "Pourquoi la clôture mensuelle échoue sur une écriture verrouillée, et comment la débloquer.",
                    """
                    ## Symptômes

                    La clôture mensuelle échoue avec « Erreur 500 – écriture verrouillée ».

                    ## Cause la plus fréquente

                    Une écriture est restée ouverte dans une session interrompue (fermeture du navigateur pendant \
                    une saisie, coupure VPN).

                    ## Que faire

                    1. Demandez aux collègues de la comptabilité d'enregistrer ou d'annuler leurs saisies en cours.
                    2. Déconnectez-vous de l'ERP, puis reconnectez-vous et relancez la clôture.
                    3. Si l'erreur persiste, ouvrez un ticket « Logiciels métier » avec le numéro de l'écriture \
                    indiqué dans le message : le support libère le verrou côté serveur."""),
            new Article(DemoCategorySeeder.WORKSTATION, "Lenteurs / performances", SOFTWARE_TECH,
                    ArticleStatus.PUBLISHED, 90, 231, 14,
                    "Ordinateur lent : 5 vérifications avant d'ouvrir un ticket",
                    "Un poste devenu lent retrouve souvent sa vitesse avec un redémarrage complet et un peu "
                            + "d'espace disque.",
                    """
                    1. **Redémarrez vraiment** : « Arrêter » ne vide pas toujours la mémoire ; choisissez \
                    « Redémarrer ».
                    2. **Espace disque** : il faut au moins 15 % d'espace libre. Videz le dossier Téléchargements \
                    et la corbeille.
                    3. **Onglets et applications** : fermez les applications inutilisées, en particulier les \
                    onglets de navigateur avec vidéo.
                    4. **Mises à jour en attente** : une mise à jour non terminée ralentit le poste ; laissez-la \
                    s'installer.
                    5. **Gestionnaire des tâches** (Ctrl + Maj + Échap) : notez l'application qui utilise le plus \
                    de processeur.

                    Si le poste reste lent, ouvrez un ticket « Poste de travail » en indiquant l'application \
                    relevée à l'étape 5 et depuis quand la lenteur est apparue."""),
            new Article(DemoCategorySeeder.WORKSTATION, "Mises à jour", MESSAGING_TECH, ArticleStatus.PUBLISHED, 75,
                    119, 6,
                    "Mise à jour Windows bloquée à un pourcentage",
                    "Une mise à jour figée depuis des heures : attendre, redémarrer, puis lancer l'utilitaire de "
                            + "résolution des problèmes.",
                    """
                    ## Symptômes

                    L'écran « Mise à jour en cours » reste figé au même pourcentage depuis plus de deux heures.

                    ## Étapes

                    1. Vérifiez que le voyant du disque clignote : s'il clignote, la mise à jour travaille encore, \
                    attendez.
                    2. S'il ne clignote plus depuis plus d'une heure, maintenez le bouton d'alimentation 10 secondes \
                    pour éteindre, puis rallumez. Windows annule généralement la mise à jour et redémarre.
                    3. Une fois connecté : Paramètres > Système > Résolution des problèmes > Windows Update.

                    Si la mise à jour échoue à nouveau, ouvrez un ticket « Poste de travail > Mises à jour » avec le \
                    code d'erreur affiché dans l'historique des mises à jour."""),
            new Article(DemoCategorySeeder.ACCOUNTS, "Mot de passe", ACCESS_TECH, ArticleStatus.PUBLISHED, 160, 302, 17,
                    "Compte verrouillé après un changement de mot de passe",
                    "Le compte se verrouille en boucle après un changement de mot de passe : un appareil utilise "
                            + "encore l'ancien.",
                    """
                    ## Cause

                    Un appareil ou une application continue d'essayer l'ancien mot de passe : téléphone \
                    (messagerie), lecteur réseau enregistré, session ouverte sur un autre poste. Après 5 échecs, \
                    le compte est verrouillé 15 minutes.

                    ## Étapes

                    1. Sur votre téléphone, mettez à jour le mot de passe du compte de messagerie professionnelle.
                    2. Sur le poste : Panneau de configuration > Gestionnaire d'identification > supprimez les \
                    identifiants Windows enregistrés pour les serveurs de l'entreprise.
                    3. Fermez les sessions ouvertes sur d'autres postes (salle de réunion, poste partagé).
                    4. Attendez 15 minutes, puis connectez-vous avec le nouveau mot de passe.

                    Si le verrouillage continue, ouvrez un ticket « Comptes & accès » : le support identifie \
                    l'appareil fautif dans les journaux de connexion."""),
            new Article(DemoCategorySeeder.MESSAGING, "Messagerie", MESSAGING_TECH, ArticleStatus.PUBLISHED, 60, 154, 7,
                    "Outlook ne reçoit plus certains e-mails",
                    "E-mails internes reçus mais pas les externes, ou boîte qui ne se met plus à jour : les "
                            + "vérifications.",
                    """
                    ## Vérifications

                    1. Regardez le bas de la fenêtre Outlook : « Déconnecté » ou « Travail hors connexion » \
                    bloque la réception. Onglet Envoi/Réception > désactivez « Travailler hors connexion ».
                    2. Vérifiez le dossier « Courrier indésirable » et vos règles de tri (Fichier > Gérer les \
                    règles).
                    3. Consultez la messagerie depuis le navigateur (webmail) : si les e-mails y sont, le problème \
                    vient d'Outlook ; fermez-le et relancez-le.
                    4. Si une boîte est pleine, l'envoi et la réception s'arrêtent : archivez les e-mails anciens.

                    Si les e-mails externes n'arrivent nulle part, même dans le webmail, ouvrez un ticket \
                    « Messagerie & collaboration » en indiquant l'adresse d'un expéditeur concerné."""),
            new Article(DemoCategorySeeder.SECURITY, "Hameçonnage / e-mail suspect", ACCESS_TECH,
                    ArticleStatus.PUBLISHED, 130, 265, 19,
                    "Reconnaître et signaler un e-mail d'hameçonnage",
                    "Les signes qui doivent alerter, et la conduite à tenir si vous avez cliqué ou saisi vos "
                            + "identifiants.",
                    """
                    ## Les signes qui doivent alerter

                    - Urgence ou menace : « votre boîte est pleine », « votre compte sera supprimé ».
                    - Adresse de l'expéditeur qui ne correspond pas à l'organisation annoncée.
                    - Lien dont l'adresse (visible au survol) n'est pas celle de l'entreprise.
                    - Demande de mot de passe : le service informatique ne vous le demandera jamais par e-mail.

                    ## Que faire

                    1. Ne cliquez pas et n'ouvrez pas les pièces jointes.
                    2. Signalez l'e-mail en ouvrant un ticket « Sécurité > Hameçonnage », puis supprimez-le.

                    ## Si vous avez cliqué ou saisi votre mot de passe

                    Changez immédiatement votre mot de passe et ouvrez un ticket « Sécurité » avec une urgence \
                    élevée : le support vérifie les connexions récentes à votre compte."""),
            new Article(DemoCategorySeeder.MESSAGING, "Visioconférence", MESSAGING_TECH, ArticleStatus.DRAFT, 5, 0, 0,
                    "Micro non détecté en visioconférence",
                    "Le micro fonctionne ailleurs mais pas dans l'outil de visioconférence : autorisations et "
                            + "périphérique par défaut.",
                    """
                    1. Dans l'outil de visioconférence : Paramètres > Audio > choisissez le bon micro (casque ou \
                    micro intégré).
                    2. Windows : Paramètres > Confidentialité > Microphone > autorisez les applications de bureau \
                    à accéder au micro.
                    3. Débranchez puis rebranchez le casque, et redémarrez l'outil.

                    À compléter : procédure pour les casques Bluetooth."""));

    private final KnowledgeArticleRepository articleRepository;
    private final TicketCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (articleRepository.count() > 0) {
            return;
        }
        Map<String, TicketCategory> categories = categoryRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.toMap(TicketCategory::getName, Function.identity()));
        List<User> employees = new ArrayList<>(userRepository.findByRole_CodeAndActiveTrue(RoleCode.EMPLOYEE));
        Random random = new Random(42);
        Instant now = clock.instant();
        int links = 0;
        for (Article a : ARTICLES) {
            User author = userRepository.findByEmail(a.author()).orElseThrow();
            KnowledgeArticle article = new KnowledgeArticle(a.title(), a.summary(), a.content(),
                    categories.get(a.category()), author, null);
            Instant created = now.minus(Duration.ofDays(a.ageDays()));
            if (a.status() != ArticleStatus.DRAFT) {
                article.publish(created.plus(Duration.ofDays(1)));
            }
            if (a.status() == ArticleStatus.ARCHIVED) {
                article.archive();
            }
            articleRepository.saveAndFlush(article);
            jdbc.sql("""
                            update knowledge_articles set created_at = :created, updated_at = :updated, view_count = :views
                            where id = :id""")
                    .param("created", utc(created)).param("updated", utc(created.plus(Duration.ofDays(1))))
                    .param("views", a.views()).param("id", article.getId()).update();

            Collections.shuffle(employees, random);
            for (User voter : employees.subList(0, Math.min(a.helpful(), employees.size()))) {
                jdbc.sql("insert into knowledge_article_votes (article_id, user_id, created_at) values (:a, :u, :at)")
                        .param("a", article.getId()).param("u", voter.getId())
                        .param("at", utc(created.plus(Duration.ofDays(2 + random.nextInt(Math.max(1, a.ageDays() - 2))))))
                        .update();
            }
            jdbc.sql("""
                            update knowledge_articles
                            set helpful_count = (select count(*) from knowledge_article_votes where article_id = :id)
                            where id = :id""")
                    .param("id", article.getId()).update();

            if (a.status() == ArticleStatus.PUBLISHED) {
                links += jdbc.sql("""
                                insert into ticket_knowledge_links (ticket_id, article_id, linked_by, created_at)
                                select t.id, :article, t.assignee_id, t.resolved_at
                                from tickets t join ticket_subcategories s on s.id = t.subcategory_id
                                where s.name = :subcategory and t.status in ('RESOLVED', 'CLOSED')
                                  and t.resolved_at is not null
                                order by t.resolved_at desc
                                limit 3""")
                        .param("article", article.getId()).param("subcategory", a.subcategory()).update();
            }
        }
        log.info("Demo knowledge base: {} articles, {} ticket links", ARTICLES.size(), links);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
