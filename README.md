# Campus Event Hub

Production-style Spring Boot backend for campus event submission, moderation, and discovery.

## Project Overview
Campus Event Hub is a resume-ready backend service that demonstrates common enterprise patterns:
- Clean API/service/repository layering
- JWT security with role-based authorization (`USER`, `ADMIN`)
- Flyway migrations with indexed relational schema
- Idempotent create endpoint (`Idempotency-Key`)
- Validation + global consistent API errors
- Search, filters, pagination, sorting, and weekly aggregated metrics
- Structured JSON logging with correlation/request id
- Actuator health/metrics + Prometheus endpoint
- Dockerized local stack (PostgreSQL + Redis + app)
- CI workflow for build, tests, and Docker image build
- Unit + integration tests (Mockito + Testcontainers)

## Tech Stack
- Java 17
- Spring Boot 3.3.x
- Spring Web, Spring Security, Spring Data JPA
- PostgreSQL
- Flyway
- Redis (cache for category reads)
- Springdoc OpenAPI (Swagger UI)
- JUnit 5, Mockito, Testcontainers
- Docker / Docker Compose
- GitHub Actions

## Prerequisites
- Java 17+ (local JDK only needed if you run from IDE)
- Docker Desktop (required for Docker Compose and Docker-based Maven helper scripts)
- Optional: Maven 3.9+ if you want to run `mvn` directly

## Architecture
Source layout is split by responsibility:
- `controller` layer: HTTP/API contracts
- `service` layer: business rules, transactions, authorization-sensitive logic
- `repository` layer: persistence and query specs
- `entity` + `dto` + `mapper`: domain/persistence models decoupled from API contracts
- `config` / `security` / `common`: cross-cutting concerns

## Core Domain Model
- `User` (`USER`/`ADMIN`)
- `Category`
- `Event`
  - Many-to-one submitter (`User`)
  - Many-to-many categories (`event_category_map`)
  - Optional reviewer (`User`) for moderation
- `IdempotencyRecord`
  - Enforces unique key per user+operation for safe retries

## API Highlights
Base path: `/api/v1`

- Auth
  - `POST /auth/register`
  - `POST /auth/login`
- Categories
  - `GET /categories`
  - `GET /categories/{id}`
  - `POST /categories` (ADMIN)
  - `PUT /categories/{id}` (ADMIN)
  - `DELETE /categories/{id}` (ADMIN)
- Events
  - `POST /events` (AUTH, requires `Idempotency-Key`)
  - `GET /events` (public browse/search)
  - `GET /events/{eventId}`
  - `PUT /events/{eventId}` (owner/admin)
  - `DELETE /events/{eventId}` (owner/admin; cancel)
  - `POST /events/{eventId}/approve` (ADMIN)
  - `POST /events/{eventId}/reject` (ADMIN)
  - `GET /events/metrics/weekly` (ADMIN, aggregate)

Search supports:
- `q` (title/description/location text search)
- `status`
- `categoryId`
- `startFrom`, `startTo`
- `weekStart`
- Standard Spring pagination/sorting (`page`, `size`, `sort`)

## Local Setup

### Option A: No Maven/Gradle installed (Docker-only)
1. Copy env file:
   - PowerShell: `Copy-Item .env.example .env`
2. Start Docker Desktop.
3. Run stack:
   - `docker compose up --build`
4. Open:
   - API: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - Health: `http://localhost:8080/actuator/health`

### Option B: Run app in IDE/JDK and only infra in Docker
1. Start dependencies:
   - `docker compose up -d postgres redis`
2. Run the Spring Boot app from IDE.

## Running Tests

### Preferred: Maven Wrapper (no local Maven install required)
- Windows PowerShell: `.\mvnw.cmd clean verify`
- Bash/macOS/Linux: `./mvnw clean verify`

### Docker helper scripts (no local Maven required)
- PowerShell full verify: `./scripts/mvn-in-docker.ps1 clean verify`
- PowerShell compile: `./scripts/mvn-in-docker.ps1 -DskipTests compile`
- PowerShell unit test: `./scripts/mvn-in-docker.ps1 '-Dtest=com.campuseventhub.event.service.EventServiceTest' test`
- Bash full verify: `./scripts/mvn-in-docker.sh clean verify`
- Bash compile: `./scripts/mvn-in-docker.sh -DskipTests compile`

Notes:
- Integration tests use Testcontainers PostgreSQL and require Java/Testcontainers to reach your host Docker daemon directly.
- Test profile disables Redis actuator health (`management.health.redis.enabled=false`) so `/actuator/health` stays deterministic in CI even when Redis is not provisioned.
- On Windows, `mvnw` is the recommended path for full integration test execution.
- Build config pins Docker API negotiation for tests to `1.44` (`api.version`) for Docker Desktop 29.x compatibility.

Useful commands:
- Unit test only smoke run: `.\mvnw.cmd -Dtest=com.campuseventhub.event.service.EventServiceTest test`
- Compile only: `.\mvnw.cmd -DskipTests compile`

## Troubleshooting / Common Errors
- `mvn : The term 'mvn' is not recognized...`
  - Cause: Maven is not installed locally.
  - Fix: use `./scripts/mvn-in-docker.ps1 clean verify` (PowerShell) or install Maven.
- `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`
  - Cause: Docker daemon is not running.
  - Fix: start Docker Desktop and wait until it shows running, then rerun the command.
- `Docker daemon is not running. Start Docker Desktop and retry.`
  - Cause: emitted by `scripts/mvn-in-docker.ps1` when daemon check fails.
  - Fix: same as above; start Docker Desktop first.
  - Windows helper command: `Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"`
- `env file .../.env not found`
  - Cause: `docker-compose.yml` loads app environment from `.env`, but it does not exist yet.
  - Fix: copy the template first: `Copy-Item .env.example .env`, then rerun `docker compose up --build`.
- `401 Unauthorized` on `/swagger-ui.html` or `/actuator/health`
  - Cause: app is running with an older image/config where these endpoints are not explicitly permitted.
  - Fix: rebuild and restart app container: `docker compose up --build` (or for a clean restart: `docker compose down && docker compose up --build`).
- `Could not find a valid Docker environment` in Testcontainers (often with `BadRequestException Status 400` on Windows)
  - Cause: Docker is reachable by CLI, but Testcontainers cannot establish a valid daemon strategy.
  - Fix checklist:
    1. Confirm daemon: `docker info`
    2. Confirm context: `docker context ls` and use `desktop-linux` if needed
    3. Restart Docker Desktop
    4. Retry on host wrapper: `.\mvnw.cmd clean verify`
    5. If still blocked, run unit tests while investigating daemon strategy compatibility:
       - `.\mvnw.cmd -Dtest=com.campuseventhub.event.service.EventServiceTest test`
- `Error response from daemon: API returned a 400 (Bad Request) but provided no error-message`
  - Cause: Docker daemon minimum API is newer than client negotiation (seen with Docker 29.x).
  - Fix in this repo: test runs set `api.version=1.44` via Maven Surefire configuration.
  - Manual override (if needed): `.\mvnw.cmd '-Dapi.version=1.44' clean verify`
- `Unsupported Database: PostgreSQL 16.13` from Flyway
  - Cause: Flyway core without PostgreSQL database module.
  - Fix in this repo: include `org.flywaydb:flyway-database-postgresql`.
- `Connection refused` to `172.17.0.1:<random-port>` during Dockerized Maven verify
  - Cause: when tests run inside the Maven container, Testcontainers can pick the bridge gateway host for mapped ports.
  - Fix in this repo: helper scripts set `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` and `--add-host host.docker.internal:host-gateway`.
  - Workaround if it still fails in your environment:
    1. Retry the same command once.
    2. Run host wrapper instead: `.\mvnw.cmd clean verify`.

## Repo Hygiene
- Local agent bookkeeping files (`AI_WORKLOG.md`, `AI_LESSONS.md`) are intentionally gitignored.

## Seeded Local Data
`DataSeeder` creates default local users and sample event data when `APP_SEED_ENABLED=true`:
- Admin: `admin` / `Admin@123`
- User: `student1` / `Student@123`

Reference categories are seeded via Flyway migration `V2__seed_reference_data.sql`.

## Example API Calls (curl)

Register:
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Alice1234"}'
```

Login:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}'
```

Create event (idempotent):
```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Authorization: Bearer <JWT>" \
  -H "Idempotency-Key: event-001" \
  -H "Content-Type: application/json" \
  -d '{
    "title":"System Design Night",
    "description":"Designing scalable backend systems",
    "location":"Hall A",
    "startTime":"2026-04-01T17:00:00Z",
    "endTime":"2026-04-01T19:00:00Z",
    "capacity":120,
    "categoryIds":[1,2]
  }'
```

Weekly metrics (ADMIN):
```bash
curl "http://localhost:8080/api/v1/events/metrics/weekly?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z" \
  -H "Authorization: Bearer <ADMIN_JWT>"
```

More scripted calls are in `scripts/curl-examples.sh`.

## Interview Demo Flow
Use this for a recruiter/interviewer walkthrough of end-to-end behavior.

1. Start app stack (if currently down):
   - `docker compose up --build -d`
2. Run the automated curl flow:
   - `bash ./scripts/curl-examples.sh`
3. What this script demonstrates:
   - public health check
   - register/login demo user
   - login admin
   - idempotent event create + replay verification
   - admin approval
   - approved event browse/search
   - weekly admin metrics query
4. Stop stack when done:
   - `docker compose down`

Notes:
- Script uses `jq` when available, but also supports `python/python3` or Windows `powershell.exe` for JSON parsing.
- On Windows, run the script from Git Bash or WSL.
- If `bash ./scripts/curl-examples.sh` in PowerShell calls WSL and fails, run:
  `& "C:\Program Files\Git\bin\bash.exe" -lc "cd /c/Users/<your-user>/Desktop/Project && ./scripts/curl-examples.sh"`
- You can override defaults with env vars (for example `BASE_URL`, `DEMO_USERNAME`, `IDEMPOTENCY_KEY`).

## Postman Collection
- Collection file: `postman/Campus-Event-Hub.postman_collection.json`
- Import into Postman and run requests in order (`01` to `09`).
- The collection stores tokens and `eventId` in collection variables during execution.

## Observability
- JSON structured logs via `logback-spring.xml`
- Correlation id:
  - Request header: `X-Request-Id` (or generated automatically)
  - Echoed in response and added to log MDC
- Actuator endpoints:
  - `/actuator/health`
  - `/actuator/metrics`
  - `/actuator/prometheus`

## CI/CD
GitHub Actions workflow at `.github/workflows/ci.yml`:
- Build
- Run tests
- Build Docker image

## Deployment Notes
Typical deployment flow:
1. Build container image from `Dockerfile`
2. Push image to registry (GHCR/ECR/Docker Hub)
3. Deploy to container platform (ECS/Fargate, Render, Railway, Fly.io, Kubernetes)
4. Provide runtime env vars for DB/JWT/Redis

## Design Notes and Tradeoffs
- Idempotency is scoped to `user + operation + key`; replay with different payload is rejected.
- Public event listing is restricted to approved events; non-approved visibility is limited to submitter/admin.
- Cache is applied to category reads (low-churn reference data) for practical performance win with low complexity.
- `delete` is implemented as cancellation (`EventStatus.CANCELLED`) to preserve auditability.
- Data seeding uses app startup runner for realistic credentials while schema/reference data remains in Flyway.
