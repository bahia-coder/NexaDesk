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
| `dev` (défaut) | Développement local, logs détaillés, Swagger actif, port 8081 |
| `prod` | Docker / production : aucune valeur par défaut pour les secrets, Swagger désactivé |
| `demo` | Jeu de données de démonstration (à partir de la Phase 3) |
