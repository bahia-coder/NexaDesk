# NexaDesk – IT Service Management

Plateforme ITSM full stack : **Angular 21 · Spring Boot 3.5 · PostgreSQL 16 · WebSocket STOMP · Docker**.

> Projet en cours de développement. La documentation de conception est dans [`docs/phase-1`](docs/phase-1/README.md). Ce README sera complété en Phase 6.

## Structure

```
NexaDesk/
├── backend/                 API Spring Boot (Java 21, Maven)
├── frontend/                SPA Angular (standalone, signals, SCSS)
├── docs/                    Conception (analyse, architecture, ERD, API, design system)
├── docker-compose.yml       Stack complète (PostgreSQL + API + Nginx/Angular)
├── docker-compose.dev.yml   PostgreSQL seul, pour le développement local
└── .env.example             Variables d'environnement (copier en .env)
```

## Prérequis

| Outil | Version |
|---|---|
| Java (JDK) | 21 |
| Node.js | 22.12+ (Angular 21) |
| npm | 10.9.3+ ou 11 (npm 10.9.2 a un bug d'installation : `Cannot read properties of null (reading 'edgesOut')`) |
| Docker Desktop | avec WSL 2 fonctionnel sous Windows |

## Démarrage en développement

1. Base de données :
   ```bash
   docker compose -f docker-compose.dev.yml up -d
   ```
2. API (profil `dev`, port **8081**) :
   ```bash
   cd backend && ./mvnw spring-boot:run
   ```
   Swagger UI : http://localhost:8081/swagger-ui.html
3. Frontend (port 4200, proxy `/api`, `/ws` et `/actuator` vers l'API) :
   ```bash
   cd frontend && npm install && npm start
   ```
   Application : http://localhost:4200

## Démarrage avec Docker (stack complète)

```bash
cp .env.example .env    # puis remplacer les valeurs (mot de passe, secret JWT)
docker compose up -d --build
```

Application : http://localhost:8088. Nginx sert Angular et relaie `/api` et `/ws` vers l'API, sur la même origine (pas de CORS).

## Tests

```bash
cd backend && ./mvnw test        # JUnit 5 + Testcontainers (Docker requis)
cd frontend && npm test          # Vitest
```

## Profils Spring

| Profil | Usage |
|---|---|
| `dev` (défaut) | Développement local, logs détaillés, Swagger actif, port 8081, clé JWT de dev |
| `prod` | Docker / production : `JWT_SECRET` et mot de passe de base obligatoires, Swagger désactivé |
| `demo` | À combiner (`dev,demo` ou `prod,demo`) : crée les utilisateurs de démonstration et expose `/api/auth/demo-accounts` |

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

## Comptes de démonstration (profil `demo`)

Valeurs de test uniquement, sans aucune donnée réelle derrière.

| Rôle | E-mail | Mot de passe |
|---|---|---|
| Administrateur | `admin@nexadesk.test` | `Demo@Admin2026` |
| Technicien | `tech.reseau@nexadesk.test` (et 4 autres `tech.*`) | `Demo@Tech2026` |
| Employé | `employe@nexadesk.test` (et 14 autres) | `Demo@User2026` |

Le profil `demo` crée aussi 8 catégories, les jours fériés français 2026-2027 et **48 tickets** sur six mois (commentaires, interventions, historiques, évaluations). Les dates sont relatives au démarrage : la file contient toujours des tickets en retard, à risque et en attente, quel que soit le jour de la démonstration.

## SLA

- Règles par priorité (modifiables par l'admin) : Critique 15 min / 2 h en **24/7** ; Haute 1 h / 8 h, Moyenne 4 h / 24 h, Basse 8 h / 48 h en **heures ouvrées** (08:00-18:00, du lundi au vendredi, hors jours fériés).
- Le chrono de résolution est suspendu quand le ticket est en attente, résolu ou fermé ; à la reprise, l'échéance est décalée du temps ouvré de pause.
- États exposés sur chaque ticket : `ON_TRACK`, `AT_RISK` (seuil de 75 % configurable), `BREACHED`, `PAUSED`, `MET`, `MISSED`.
- Une tâche planifiée (toutes les minutes) signale une fois chaque passage à risque ou en retard, et clôt les tickets résolus restés sans réponse 72 h ouvrées.

## Notifications temps réel

- Les services publient des événements de domaine (`TicketEvent`). Un écouteur `@TransactionalEventListener(AFTER_COMMIT)` les transforme en notifications : jamais de notification pour une action annulée.
- Chaque notification est enregistrée (centre de notifications, badge de non lues), puis poussée en **STOMP over WebSocket** sur `/ws`, file `/user/queue/notifications`.
- Le JWT est vérifié dans la trame `CONNECT`. Seul l'abonnement à sa propre file est autorisé, et le client ne peut rien publier.
- API : `GET /api/notifications`, `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`.

## Authentification

- `POST /api/auth/login` renvoie un **access token JWT** (15 min) à envoyer en `Authorization: Bearer`, et pose un **refresh token** opaque dans un cookie `httpOnly`, `Secure`, `SameSite=Strict`, limité à `/api/auth` (7 jours).
- `POST /api/auth/refresh` fait **tourner** le refresh token. Réutiliser un ancien token (au-delà de 10 s de tolérance pour les onglets concurrents) révoque toute la session : c'est la détection de vol.
- 5 échecs de connexion verrouillent le compte 15 minutes.
- Un compte créé ou réinitialisé par l'admin reçoit un **mot de passe temporaire**. Tant qu'il n'est pas changé, le serveur refuse toute requête hors `/api/auth` et `/api/profile` (`403 PASSWORD_CHANGE_REQUIRED`).
- Changer son mot de passe met fin à toutes les autres sessions. Désactiver un compte ou changer son rôle révoque aussi ses sessions.
- Toutes les erreurs suivent le format RFC 9457 (`application/problem+json`) avec un `code` stable et un `traceId` (en-tête `X-Trace-Id`).
