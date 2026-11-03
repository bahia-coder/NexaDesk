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
| Mémoire | Les JVM de Maven et des tests sont plafonnées (`backend/.mvn/jvm.config`, `argLine` de Surefire) pour tourner sur une machine de 8 Go avec Docker |
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

### Tests de bout en bout (Playwright)

Ils pilotent un vrai navigateur sur l'application complète, avec les données de démonstration : connexion et
session, menus et pages interdites de chaque rôle, et le parcours d'un ticket entre une employée et un technicien
(création, prise en charge, résolution suivie en direct par WebSocket, confirmation).

```bash
# .env : SPRING_PROFILES_ACTIVE=prod,demo
docker compose up -d --build
cd frontend && npm run e2e
```

- Par défaut, ils visent la stack Docker (`http://localhost:8088`) ; pour les serveurs de développement :
  `E2E_BASE_URL=http://localhost:4200 npm run e2e`.
- Ils utilisent Microsoft Edge installé sur le poste. Sans Edge (Linux, CI) : `npx playwright install chromium`, puis
  `E2E_CHANNEL= npm run e2e`.
- Aucun identifiant n'est écrit dans les tests : ils lisent les comptes de démonstration sur
  `/api/auth/demo-accounts`. Chaque exécution crée ses propres tickets.

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

Le profil `demo` crée aussi 8 catégories, 10 équipements (reliés aux tickets concernés), les jours fériés français 2026-2027 et **48 tickets** sur six mois (commentaires, interventions, historiques, évaluations). Les dates sont relatives au démarrage : la file contient toujours des tickets en retard, à risque et en attente, quel que soit le jour de la démonstration.

## SLA

- Règles par priorité (modifiables par l'admin) : Critique 15 min / 2 h en **24/7** ; Haute 1 h / 8 h, Moyenne 4 h / 24 h, Basse 8 h / 48 h en **heures ouvrées** (08:00-18:00, du lundi au vendredi, hors jours fériés).
- Le chrono de résolution est suspendu quand le ticket est en attente, résolu ou fermé ; à la reprise, l'échéance est décalée du temps ouvré de pause.
- États exposés sur chaque ticket : `ON_TRACK`, `AT_RISK` (seuil de 75 % configurable), `BREACHED`, `PAUSED`, `MET`, `MISSED`.
- Une tâche planifiée (toutes les minutes) signale une fois chaque passage à risque ou en retard, et clôt les tickets résolus restés sans réponse 72 h ouvrées.

## Dashboards, rapports et audit

- **Dashboards par rôle** (`/api/dashboard/admin|technician|employee`). Tous les chiffres sont calculés en SQL agrégé sur PostgreSQL, avec une seule définition par indicateur (`AnalyticsQueries`) : indicateurs avec tendance par rapport à la période précédente, évolution, répartitions, performance des techniciens, respect du SLA, satisfaction et activité récente.
- **Rapport filtrable** (`/api/reports/summary` : période, catégorie, technicien ; un technicien ne voit que ses chiffres), avec export **Excel** (Apache POI, 6 feuilles) et **PDF** (OpenPDF, police intégrée : aucune dépendance aux polices du serveur).
- **Journal d'audit** (`/api/audit`, admin) : connexions réussies et échouées (avec le motif), comptes, rôles, configuration (catégories, priorités, SLA, jours fériés), équipements et actions clés sur les tickets. Chaque entrée contient l'auteur, l'adresse IP, le `traceId`, et les valeurs avant et après en JSONB. L'audit s'écrit dans la transaction de l'action : une action annulée ne laisse pas de trace.

## Base de connaissances

- Articles rédigés par les techniciens (brouillons), publiés puis archivés par l'admin. Un brouillon peut être généré à partir d'un ticket résolu : la description devient les symptômes, la résolution devient la solution.
- **Recherche plein texte PostgreSQL** (configuration `french` : « imprimantes » trouve « imprimante »), colonnes `tsvector` générées et index GIN, complétée par la similarité trigramme (`pg_trgm`) pour les fautes de frappe. Un employé ne voit que les articles publiés.
- Compteur de vues, vote « cet article m'a aidé » (une seule fois par utilisateur, garanti par la clé primaire), liens entre tickets et articles.
- Recherche globale du header (`/api/search`) : tickets et articles ; pour l'admin, aussi les utilisateurs et les équipements.

## IA

L'application fonctionne entièrement sans IA. **Chaque réponse indique sa source** (`LLM`, `HEURISTIC` ou `DISABLED`) avec une mention à afficher : on ne présente jamais un résultat local comme une réponse d'IA.

| Fonction | Sans modèle (par défaut) | Avec un modèle connecté |
|---|---|---|
| Incidents et articles similaires | Toujours actifs : recherche plein texte et trigrammes PostgreSQL, en respectant la visibilité des tickets | idem |
| Classification (catégorie, impact, urgence → priorité) | Mots-clés de chaque catégorie et formulations (« bloqué », « toute l'équipe ») | Réponse JSON validée ; la catégorie doit exister |
| Résumé d'un ticket | Résumé extractif (faits du ticket), présenté comme « non IA » | Résumé rédigé |
| Pistes de résolution | Solutions des tickets similaires résolus et articles | Étapes rédigées à partir de ces sources uniquement |
| Assistant | Articles les plus pertinents | Réponse rédigée à partir des articles trouvés (RAG simple), articles cités |

Configuration (variables d'environnement, jamais dans Git) :

```bash
AI_MODE=llm AI_PROVIDER=anthropic AI_API_KEY=...                          # Claude (SDK officiel), claude-opus-5-5 par défaut
AI_MODE=llm AI_PROVIDER=openai-compatible AI_BASE_URL=http://localhost:11434/v1 AI_MODEL=llama3.2   # Ollama local, gratuit
```

- Si la configuration `llm` est incomplète, l'API démarre en mode heuristique et l'indique dans les logs.
- Garde-fous :
  - délai maximal par appel (`AI_TIMEOUT`, 20 s) ;
  - limite par utilisateur (`AI_REQUESTS_PER_MINUTE`, 10) ;
  - réponse JSON validée strictement : un identifiant de source inventé est rejeté.

  En cas d'échec, l'heuristique répond à la place, et la réponse le signale.
- Minimisation des données : le modèle reçoit le contenu des tickets et des articles, jamais un nom ni un e-mail (les personnes sont désignées par leur rôle).
- Avec Anthropic : structured outputs (schéma JSON imposé), effort `low` et repli côté serveur en cas de refus (`fallbacks`, bêta).

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
