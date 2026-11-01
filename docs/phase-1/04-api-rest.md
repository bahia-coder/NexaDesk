# 04 — API REST

Base : `/api` · Format : JSON · Erreurs : `application/problem+json` (RFC 9457) · Documentation : `/swagger-ui.html`, spec `/v3/api-docs`.

**Conventions**
- Pagination : `?page=0&size=20&sort=createdAt,desc`. Les champs triables sont en liste blanche, et la taille est plafonnée à 100.
- Codes : `200` lecture/maj · `201` création (+ en-tête `Location`) · `204` sans contenu · `400` validation · `401` non authentifié · `403` rôle insuffisant · `404` introuvable **ou non accessible** · `409` conflit de version / transition interdite · `413` fichier trop gros · `422` règle métier violée.
- Les actions métier (assigner, changer de statut, rouvrir) sont des **sous-ressources dédiées** (`POST /tickets/{id}/assign`) plutôt qu'un `PATCH` générique : chaque action a sa validation, sa règle d'autorisation, son historique et sa documentation Swagger propres.
- Colonne **Accès** : E = Employee, T = Technician, A = Admin, ★ = restriction à la ressource vérifiée dans le service.

## Authentification — `/api/auth`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| POST | `/auth/login` | public | E-mail + mot de passe → access token + cookie refresh |
| POST | `/auth/refresh` | cookie | Rotation du refresh token → nouvel access token |
| POST | `/auth/logout` | authentifié | Révoque le refresh token, efface le cookie |
| GET | `/auth/me` | authentifié | Utilisateur courant + rôle |
| GET | `/auth/demo-accounts` | public, **profil `demo` uniquement** | Liste des comptes de démonstration pour la page de connexion |

## Profil — `/api/profile`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/profile` | E T A | Mon profil |
| PUT | `/profile` | E T A | Modifier téléphone, poste, couleur d'avatar |
| PUT | `/profile/password` | E T A | Changer de mot de passe (mot de passe actuel exigé ; révoque les autres sessions) |

## Utilisateurs — `/api/users` (admin)

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/users?search=&role=&active=&departmentId=` | A | Liste paginée et filtrée |
| GET | `/users/{id}` | A | Détail + statistiques (tickets demandés / traités) |
| POST | `/users` | A | Créer (mot de passe temporaire généré, à changer à la première connexion) |
| PUT | `/users/{id}` | A | Modifier les informations |
| PUT | `/users/{id}/role` | A | Changer le rôle (un admin ne peut pas se retirer lui-même son rôle) |
| PUT | `/users/{id}/specialties` | A | Définir les spécialités d'un technicien |
| POST | `/users/{id}/deactivate` | A | Désactiver (409 avec la liste des tickets si c'est un technicien avec des tickets ouverts, RM-13) |
| POST | `/users/{id}/activate` | A | Réactiver |
| GET | `/users/technicians?categoryId=` | T A | Techniciens actifs avec leur charge actuelle (pour la réaffectation) |
| GET | `/departments` | E T A | Liste des départements |

## Référentiels — catégories et priorités

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/categories?includeInactive=` | E T A | Catégories avec leurs sous-catégories |
| POST | `/categories` | A | Créer |
| PUT | `/categories/{id}` | A | Modifier (nom, icône, mots-clés) |
| POST | `/categories/{id}/deactivate` | A | Désactiver (refusé si des tickets ouverts l'utilisent) |
| POST | `/categories/{id}/subcategories` | A | Ajouter une sous-catégorie |
| PUT | `/subcategories/{id}` | A | Modifier |
| POST | `/subcategories/{id}/deactivate` | A | Désactiver |
| GET | `/priorities` | E T A | Priorités avec leur SLA |
| PUT | `/priorities/{id}` | A | Modifier le libellé et la couleur |
| GET | `/priorities/matrix` | E T A | Matrice impact × urgence (pour l'aperçu dans le formulaire) |

## Tickets — `/api/tickets`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/tickets?search=&status=&priority=&type=&categoryId=&assigneeId=&requesterId=&slaState=&scope=&from=&to=` | E★ T★ A | Liste paginée ; `status` et `priority` sont multi-valeurs ; `scope` ∈ `mine`, `assigned`, `unassigned`, `all` |
| GET | `/tickets/board?…mêmes filtres` | T★ A | Tickets groupés par statut pour le Kanban (limite par colonne) |
| GET | `/tickets/{id}` | E★ T★ A | Détail + `allowedTransitions` + état SLA |
| GET | `/tickets/by-reference/{ref}` | E★ T★ A | Accès par référence (recherche globale, liens partagés) |
| POST | `/tickets` | E T A | Créer (multipart possible avec les pièces jointes) |
| PUT | `/tickets/{id}` | E★ T★ A | Modifier titre / description / équipement (RM-04) |
| PUT | `/tickets/{id}/classification` | T★ A | Changer la catégorie ou la priorité (motif obligatoire, recalcul SLA) |
| POST | `/tickets/{id}/take` | T★ | Prise en charge par soi-même (RM-05) |
| POST | `/tickets/{id}/assign` | A | Affecter / réaffecter `{ technicianId, comment }` |
| GET | `/tickets/{id}/allowed-transitions` | E★ T★ A | Transitions possibles pour l'utilisateur courant |
| POST | `/tickets/{id}/status` | T★ A | Transition `{ targetStatus, reason?, pendingReason? }` |
| POST | `/tickets/{id}/confirm-resolution` | E★ | Confirmer → `CLOSED` |
| POST | `/tickets/{id}/reject-resolution` | E★ | Refuser la résolution `{ reason }` → `IN_PROGRESS` |
| POST | `/tickets/{id}/reopen` | E★ A | Rouvrir un ticket `CLOSED` `{ reason }` |
| GET | `/tickets/{id}/history` | E★ T★ A | Timeline (filtrée selon le rôle : pas d'événements internes pour E) |
| GET | `/tickets/{id}/comments` | E★ T★ A | Commentaires (sans les internes pour E) |
| POST | `/tickets/{id}/comments` | E★ T★ A | Ajouter `{ body, internal }` (`internal=true` refusé pour E) |
| GET | `/tickets/{id}/attachments` | E★ T★ A | Liste |
| POST | `/tickets/{id}/attachments` | E★ T★ A | Upload multipart (RM-15) |
| GET | `/tickets/{id}/attachments/{attachmentId}` | E★ T★ A | Téléchargement sécurisé |
| DELETE | `/tickets/{id}/attachments/{attachmentId}` | E★ (ticket `NEW`, ses fichiers) A | Supprimer |
| POST | `/tickets/{id}/feedback` | E★ | Évaluer `{ rating, comment }` (RM-10) |
| PUT | `/tickets/{id}/knowledge-links` | T★ A | Lier des articles de connaissance |

## Interventions

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/tickets/{id}/interventions` | E★ (solution seulement) T★ A | Interventions du ticket |
| POST | `/tickets/{id}/interventions` | T★ A | Ajouter `{ diagnosis, solution?, timeSpentMinutes, startedAt?, endedAt? }` |
| PUT | `/interventions/{id}` | T★ (auteur, ticket non fermé) A | Modifier |
| GET | `/interventions/mine?from=&to=` | T | Mon historique d'interventions + temps total |

## SLA — `/api/sla` (admin)

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/sla/configurations` | T A | Règles par priorité |
| PUT | `/sla/configurations/{id}` | A | Modifier une règle (les échéances des tickets existants sont conservées ; la règle s'applique aux nouveaux tickets) |
| GET | `/sla/calendar` | A | Calendrier ouvré |
| PUT | `/sla/calendar` | A | Modifier le fuseau, les horaires et les jours ouvrés |
| GET / POST / DELETE | `/sla/holidays[/{id}]` | A | Jours fériés |
| GET | `/sla/preview?priorityId=&from=` | A | Simulation des échéances (pour vérifier la configuration avant de l'enregistrer) |

## Notifications — `/api/notifications`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/notifications?unreadOnly=&page=` | E T A | Mes notifications |
| GET | `/notifications/unread-count` | E T A | Compteur (chargement initial ; ensuite mis à jour via WebSocket) |
| POST | `/notifications/{id}/read` | E T A | Marquer comme lue |
| POST | `/notifications/read-all` | E T A | Tout marquer comme lu |

**WebSocket :** endpoint `/ws` (STOMP) · abonnement `/user/queue/notifications` · payload `NotificationDto`. Option : `/topic/tickets.board` pour rafraîchir le Kanban des techniciens et de l'admin en direct.

## Équipements — `/api/equipment`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/equipment?search=&type=&status=&assignedUserId=` | E★ (les siens) T A | Liste paginée |
| GET | `/equipment/{id}` | E★ T A | Détail |
| GET | `/equipment/{id}/tickets` | T A | Historique des incidents liés |
| GET | `/equipment/{id}/assignments` | T A | Historique des affectations |
| POST | `/equipment` | A | Créer |
| PUT | `/equipment/{id}` | A | Modifier |
| PUT | `/equipment/{id}/status` | T A | Changer l'état (ex. `IN_REPAIR`) |
| POST | `/equipment/{id}/assign` | A | Affecter à un utilisateur `{ userId }` (ou `null` pour remettre en stock) |
| POST | `/equipment/{id}/retire` | A | Mettre au rebut |

## Dashboard — `/api/dashboard`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/dashboard/admin?period=30d` | A | KPI globaux, répartition par priorité, évolution, performance des techniciens, SLA |
| GET | `/dashboard/technician` | T | Mes tickets, en attente, urgents, résolus cette semaine, temps moyen, répartition |
| GET | `/dashboard/employee` | E | Ouverts, en cours, résolus, dernières demandes |
| GET | `/dashboard/activity?limit=10` | T★ A | Activité récente (depuis `ticket_history`) |

Toutes les valeurs sont calculées par des requêtes agrégées (`COUNT … FILTER (WHERE …)`, `AVG`, `date_trunc('day'|'month')`). Le **temps moyen de résolution** est la moyenne de `resolved_at − created_at − pauses`.

## Rapports — `/api/reports`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/reports/summary?from=&to=&categoryId=&technicianId=` | A (T★ : limité à lui-même) | Par catégorie, priorité, technicien ; temps moyen ; taux SLA ; évolution mensuelle ; satisfaction |
| GET | `/reports/export.pdf?…` | A | Export PDF (OpenPDF) |
| GET | `/reports/export.xlsx?…` | A | Export Excel (Apache POI), un onglet par indicateur |

## Base de connaissances — `/api/knowledge`

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/knowledge?search=&categoryId=&status=` | E (publiés) T A | Recherche plein texte |
| GET | `/knowledge/{id}` | E★ T A | Lecture (incrémente `view_count`) |
| POST | `/knowledge` | T A | Créer un brouillon |
| PUT | `/knowledge/{id}` | T★ (auteur) A | Modifier |
| POST | `/knowledge/{id}/publish` | A | Publier |
| POST | `/knowledge/{id}/archive` | A | Archiver |
| POST | `/knowledge/{id}/helpful` | E T A | « Cet article m'a aidé » (une fois par utilisateur) |
| POST | `/knowledge/from-ticket/{ticketId}` | T★ A | Générer un brouillon à partir de la solution d'un ticket |

## IA — `/api/ai`

Chaque réponse contient `source: "LLM" | "HEURISTIC" | "DISABLED"` et un `disclaimer`.

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/ai/status` | E T A | Mode actif (pour afficher le bon badge dans l'UI) |
| POST | `/ai/classify` | E T A | `{ title, description }` → catégorie, sous-catégorie, priorité suggérées + score de confiance |
| GET | `/ai/tickets/{id}/summary` | T★ A | Résumé de l'historique |
| GET | `/ai/tickets/{id}/suggestions` | T★ A | Solutions proposées (tickets similaires résolus + articles) |
| GET | `/ai/tickets/{id}/similar?limit=5` | T★ A | Incidents similaires avec score (PostgreSQL FTS + trigrammes) |
| POST | `/ai/similar` | E T A | `{ title, description }` → similaires **avant création** (« Ce problème a peut-être déjà une solution ») |
| POST | `/ai/assistant` | E T A | `{ question, history[] }` → réponse + articles sources cités |

## Audit — `/api/audit` (admin)

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/audit?actorId=&action=&entityType=&from=&to=` | A | Journal paginé |

## Système

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/actuator/health` | public | Healthcheck Docker |
| GET | `/search?q=` | E★ T★ A | Recherche globale du header : tickets (par référence ou texte), articles, et, pour l'admin, utilisateurs et équipements |
