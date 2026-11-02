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
| Solution (RM-07) | Exige une intervention avec solution | Champ `resolution` obligatoire pour passer en `RESOLVED` ; s'il est vide, la solution de la dernière intervention est reprise | La solution reste lisible sur le ticket ; le technicien ne la retape pas |
| Interventions | Employé : solution seulement | Réservées aux techniciens et admins ; saisie sur ticket en cours ou en attente | Le diagnostic est technique ; l'employé lit la solution dans `resolution`. Le module ticket ne dépend pas du module intervention (interface `SolutionSource`) |
| Pièces jointes | Liables à un commentaire | Liées au ticket uniquement | Plus simple, sans perte fonctionnelle |
| Réaffectation | `ASSIGNED → ASSIGNED` | Possible sur tout ticket ouvert, statut conservé | Un admin doit pouvoir réaffecter un ticket en cours |
| Clôture automatique (RM-08) | Module tickets | Module SLA (`SlaJobs`) | Elle dépend du calendrier ouvré |
| Pause SLA | Pendant `PENDING` et après réouverture | Pendant `PENDING`, `RESOLVED` et `CLOSED` ; à la reprise, l'échéance est décalée du temps ouvré de pause | Une seule règle couvre l'attente, le refus de résolution et la réouverture |
| Fuseau horaire | Dans le calendrier ouvré (table) | Configuration de déploiement (`APP_TIMEZONE`) ; la table garde horaires et jours ouvrés | Le fuseau sert aussi aux années des références et à l'affichage |
| API SLA | `/api/sla/configurations` | `/api/sla/rules`, `/calendar`, `/holidays`, `/preview` | Vocabulaire plus clair (« règle ») |
| Équipements partagés | « En service » = affecté à un utilisateur | `IN_USE` sans titulaire = matériel partagé à un emplacement (imprimante, serveur) ; emplacement obligatoire | Une imprimante d'étage n'appartient à personne |
| RM-14 | L'employé choisit parmi ses équipements | Ses équipements **et** le matériel partagé ; technicien et admin : tout le parc actif (`/api/equipment/selectable`) | L'employé doit pouvoir signaler une panne d'imprimante |
| Code d'inventaire | Non précisé | Séquence PostgreSQL `equipment_code_seq` (`EQ-0001`) | Sûre en concurrence, jamais réutilisée |
| Fournisseur LLM | À choisir en Phase 3 | Deux connecteurs : API Anthropic (SDK Java officiel, structured outputs) et API OpenAI-compatible (Ollama, OpenAI, Mistral…), choisis par `AI_PROVIDER` | Démonstratif en entretien et réversible ; Ollama permet un mode réel gratuit en local |
| Module IA | `HeuristicAiProvider` implémente `AiProvider` | `LlmClient` ne transporte que les appels au modèle ; l'heuristique est une logique séparée (`HeuristicClassifier`, `HeuristicSummary`) et `AiService` choisit, valide et bascule | Une heuristique n'est pas un « fournisseur de complétion » : chaque chemin reste testable seul |
| Timeout LLM | 10 s | 20 s par défaut (`AI_TIMEOUT`), une seule nouvelle tentative | Les modèles récents réfléchissent avant de répondre ; au-delà, l'heuristique répond |
| Pertinence de la similarité | Non précisé | `ts_rank` (titre pondéré) + 0,3 × similarité trigramme ; seuils absolu (0,1) et relatif (50 % du meilleur) ; score affiché relatif au meilleur résultat | Une longue description partage toujours quelques mots avec tout : ces seuils évitent les faux positifs mesurés sur la démo |
| Assistant sans article | Le LLM répond | Si aucun article n'est pertinent, le modèle n'est pas appelé et la création d'un ticket est proposée | Pas de réponse inventée hors de la base de connaissances, et pas de coût inutile |
| Liens ticket ↔ article | Technicien du ticket | Technicien assigné ou admin, articles publiés uniquement ; le demandeur ne voit que les articles publiés | Un brouillon n'est pas une solution validée |
| Alertes SLA | Une par seuil | Une par seuil, réarmée si une reprise repousse l'échéance dans le futur ; indicateurs posés par `UPDATE` ciblé, sans incrémenter `@Version` | Pas de conflit `409` avec un technicien qui modifie le ticket au même moment |

## Points à valider

1. **Références `INC-` et `REQ-`** : deux séries distinctes (incident / demande de service), ou une série `INC-` unique pour tout ?
2. **Fuseau et horaires par défaut** : `Europe/Paris`, du lundi au vendredi, de 08:00 à 18:00 ? (À adapter si l'entreprise fictive est ailleurs, par exemple `Africa/Casablanca`.)
3. **Langue de l'interface** : français uniquement (proposé), ou FR + EN ?
4. **Fournisseur LLM pour le mode IA réel** : à trancher en Phase 3 ; le mode heuristique reste l'option par défaut.
5. **Maquette HTML interactive** du design avant la Phase 4 : oui / non ?
