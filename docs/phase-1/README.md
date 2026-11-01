# NexaDesk — Phase 1 : Analyse et conception

| # | Document | Contenu |
|---|---|---|
| 01 | [Analyse fonctionnelle](01-analyse-fonctionnelle.md) | Besoin, acteurs, matrice des permissions, workflow, règles métier, SLA, notifications, IA, données de démo |
| 02 | [Architecture technique](02-architecture.md) | Vue d'ensemble, versions, backend Spring Boot, sécurité JWT, frontend Angular, tests, Docker |
| 03 | [Base de données](03-base-de-donnees.md) | ERD Mermaid, relations, enums, contraintes, index, DTO |
| 04 | [API REST](04-api-rest.md) | Tous les endpoints par module, avec les droits d'accès |
| 05 | [Design system et maquettes](05-design-system-maquettes.md) | Tokens, composants, wireframes, règles UX |

## Décisions d'architecture clés (à savoir défendre en entretien)

| Décision | Alternative écartée | Argument |
|---|---|---|
| Monolithe modulaire, package-by-feature | Microservices | Un seul domaine, une équipe : la complexité distribuée n'apporte rien ici |
| Access JWT en mémoire + refresh token httpOnly avec rotation | JWT dans `localStorage` | Résistance au XSS, sessions révocables |
| Contrôle d'accès en deux niveaux (rôle + propriété de la ressource) | `@PreAuthorize` seul | Un rôle ne suffit pas : un employé ne doit voir que **ses** tickets |
| Workflow défini dans une table de transitions unique côté serveur | Règles dispersées, ou dupliquées dans Angular | Une seule source de vérité, testable exhaustivement |
| SLA en heures ouvrées avec pause, `Clock` injecté | Simple `created_at + n heures` | Conforme aux pratiques ITSM, testable de façon déterministe |
| Événements de domaine + `AFTER_COMMIT` pour les notifications | Appel direct au WebSocket depuis le service | Découplage, et aucune notification fantôme si la transaction échoue |
| Flyway + `ddl-auto=validate` | `ddl-auto=update` | Schéma versionné et reproductible |
| Similarité via PostgreSQL FTS + `pg_trgm` | Embeddings via une API payante | Gratuit, réellement fonctionnel, la bonne technologie au bon endroit |
| Fournisseur IA derrière une interface, mode heuristique par défaut | Dépendre d'une API payante | L'application marche sans clé, et l'origine de chaque réponse est affichée honnêtement |
| Un seul `app-shell` avec navigation configurée par rôle | Trois layouts | Pas de duplication |
| Signals pour l'état, RxJS pour les flux, pas de NgRx | NgRx | Proportionné à la taille de l'application |
| Testcontainers PostgreSQL | H2 | Les tests utilisent les fonctionnalités réellement employées en production |

## Écarts de mise en œuvre (Phase 3)

Décisions prises pendant le développement, qui affinent la conception ci-dessus :

| Sujet | Conception initiale | Mise en œuvre | Raison |
|---|---|---|---|
| Références de ticket | `SELECT … FOR UPDATE` sur le compteur | `INSERT … ON CONFLICT DO UPDATE … RETURNING` | Atomique, et couvre aussi le premier ticket de l'année (aucune ligne à verrouiller) |
| Actions possibles | `GET /tickets/{id}/allowed-transitions` | Champ `availableActions` dans le détail du ticket | Une requête de moins ; même source de vérité (`TicketPolicy`) |
| Visibilité technicien | Tickets assignés et non assignés de ses spécialités | Tickets assignés, tickets demandés et **tous** les tickets de ses spécialités | Permet l'entraide (notes internes) au sein d'une même spécialité |
| Solution (RM-07) | Exige une intervention avec solution | Champ `resolution` obligatoire pour passer en `RESOLVED` | Les interventions (module 5) détaillent le travail et le temps passé ; la solution reste lisible sur le ticket |
| Pièces jointes | Liables à un commentaire | Liées au ticket uniquement | Plus simple, sans perte fonctionnelle |
| Réaffectation | `ASSIGNED → ASSIGNED` | Possible sur tout ticket ouvert, statut conservé | Un admin doit pouvoir réaffecter un ticket en cours |
| Clôture automatique (RM-08) | Module tickets | Module SLA | Elle dépend du calendrier ouvré |

## Points à valider

1. **Références `INC-` et `REQ-`** : deux séries distinctes (incident / demande de service), ou une série `INC-` unique pour tout ?
2. **Fuseau et horaires par défaut** : `Europe/Paris`, du lundi au vendredi, de 08:00 à 18:00 ? (À adapter si l'entreprise fictive est ailleurs, par exemple `Africa/Casablanca`.)
3. **Langue de l'interface** : français uniquement (proposé), ou FR + EN ?
4. **Fournisseur LLM pour le mode IA réel** : à trancher en Phase 3 ; le mode heuristique reste l'option par défaut.
5. **Maquette HTML interactive** du design avant la Phase 4 : oui / non ?
