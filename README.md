# Work Order API

A small Spring Boot REST API for creating and managing service work orders. I built this project as a practical backend demo covering REST endpoints, validation, persistence, basic authentication, testing, and API documentation.

## Features

- REST CRUD endpoints for work orders
- Status and priority filtering with pagination
- PostgreSQL persistence through Spring Data JPA
- Request validation and centralized API error handling
- HTTP Basic protection for write operations
- Public Swagger/OpenAPI documentation
- Actuator health endpoint
- Fast JUnit 5, Mockito, MockMvc, and H2 tests plus full-context PostgreSQL Testcontainers HTTP tests
- Versioned PostgreSQL schema through Flyway in explicitly enabled environments
- Opt-in fictional demo records in development only, when the database is empty

## Tech Stack

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- Spring Security
- Spring Boot Actuator
- JUnit 5 and MockMvc
- H2 for fast repository tests; disposable PostgreSQL 16.10 via Testcontainers for integration tests
- springdoc-openapi / Swagger UI
- Maven and Maven Wrapper

## Architecture

```text
Client
  |
  v
WorkOrderController
  |
  v
WorkOrderService
  |
  v
WorkOrderRepository
  |
  v
PostgreSQL
```

- `WorkOrderController` handles HTTP requests, validation, and response status codes.
- `WorkOrderService` contains application operations and maps entities to response DTOs.
- `WorkOrderRepository` provides persistence and the small set of filter queries.
- PostgreSQL stores work orders normally and in the container-backed integration suite; the fast repository test uses H2.

## API Endpoints

| Method | Endpoint | Description | Authentication |
| --- | --- | --- | --- |
| `GET` | `/api/work-orders` | List and filter work orders | Public |
| `GET` | `/api/work-orders/{id}` | Get one work order | Public |
| `POST` | `/api/work-orders` | Create a work order | HTTP Basic |
| `PUT` | `/api/work-orders/{id}` | Update work order details | HTTP Basic |
| `PATCH` | `/api/work-orders/{id}/status` | Update only the status | HTTP Basic |
| `DELETE` | `/api/work-orders/{id}` | Delete a work order | HTTP Basic |

New work orders always start with the `OPEN` status. Valid status values are `OPEN`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`; valid priorities are `LOW`, `MEDIUM`, and `HIGH`.

## Filtering

Filters can be used separately or together, along with Spring's `page` and `size` parameters:

```text
GET /api/work-orders?status=OPEN
GET /api/work-orders?priority=HIGH
GET /api/work-orders?status=OPEN&priority=HIGH
GET /api/work-orders?page=0&size=10
```

## Running Tests

macOS/Linux:

```bash
./mvnw test
```

Windows:

```powershell
mvnw.cmd test
```

The default `test` command runs both the existing fast tests and `WorkOrderPostgresIntegrationTest`. Java 21 and a running Docker engine that supports Linux containers are required for the full suite. You do not need a host PostgreSQL installation. On Windows, start Docker Desktop manually and verify `docker info` reports a Linux server. The first run may download the PostgreSQL image and Testcontainers cleanup image.

The integration suite starts `postgres:16.10-alpine` with a disposable database and a real Spring Boot application on a random loopback HTTP port. It uses the actual security filter chain, controller, service and JPA persistence, not mocked beans or H2. It covers unauthorized/invalid writes, 201/Location creation, committed status updates and public retrieval, malformed status, combined filters and sorted pagination.

Before each case, the test verifies the datasource product, URL and database name match the running container, then clears only that disposable table. Fixture counts and IDs do not depend on startup demo records. Explicit test-only credentials and container datasource bindings override application defaults; no deployed secrets or database are used. Tests do not run inside a rollback-only test transaction, so independent JDBC reads verify committed HTTP writes.

Run only the PostgreSQL suite:

```powershell
.\mvnw.cmd "-Dtest=WorkOrderPostgresIntegrationTest" test
```

Run the existing fast suites without starting a PostgreSQL container:

```powershell
.\mvnw.cmd "-Dtest=WorkOrderServiceTest,WorkOrderControllerTest,WorkOrderRepositoryTest" test
```

On macOS/Linux, replace `.\mvnw.cmd` with `./mvnw`. H2 is retained only in the fast repository suite; it is never a fallback for the PostgreSQL tests. If Docker or PostgreSQL startup fails, the full suite fails rather than skipping integration coverage. The existing GitHub Actions `./mvnw test` step automatically discovers this `*Test` class; no opt-in profile or separate CI step is needed.

Surefire summaries are written to `target/surefire-reports`. PostgreSQL tests now run Flyway V1 and Hibernate `validate`. `SchemaLifecycleIntegrationTest` also verifies migration history, schema metadata/constraints, restart/data retention, rejected unmanaged schemas, environment guards and opt-in development seeding. Fast H2 repository tests retain their separate `create-drop` profile with Flyway disabled. Docker startup failure still fails the full suite.

## Building

macOS/Linux:

```bash
./mvnw clean package
```

Windows:

```powershell
mvnw.cmd clean package
```

## Production safety and adoption gate

**Do not deploy M2 to the existing portfolio database without a separately approved adoption procedure.** Production/default configuration now requires explicit database and application credentials, disables Flyway and uses Hibernate `validate`. Missing/blank credentials reject startup; an incompatible existing schema may also reject startup. No production credential verification, database connection, baseline, migration, deployment or restart was performed.

See [proposed production adoption procedure](docs/PRODUCTION_ADOPTION.md) for schema inventory, verified backup/restore rehearsal, explicit baseline compatibility and rollback planning. `baseline-on-migrate` is false and Flyway clean is disabled. Activating migrations on a non-empty unmanaged database fails instead of silently baselining it.

Existing Basic authentication, disabled CSRF and authentication-only write rules remain unchanged. Stateless Basic can need CSRF protection for browser clients that automatically attach credentials. Public reads include requester email, so demo data must remain fictional. Production defaults were removed from code; the actual deployed credential values remain unverified.

## Local Docker Compose

A non-root Java 21 runtime image and local API/PostgreSQL Compose stack are available. Package the tested JAR with `./mvnw clean verify`, configure an ignored local credential file, then build/start Compose. The API binds to localhost; PostgreSQL has no published host port. Flyway runs only in the local dev profile, Hibernate validates, demo seeding is opt-in, and a named volume preserves database state.

See [Docker development runbook](docs/DOCKER_DEVELOPMENT.md) for exact commands, health checks, persistence verification, cleanup, configuration failures and troubleshooting. `python scripts/verify_compose.py` verifies a new disposable project using generated local-only credentials and cleans only its resources. CI also packages/builds/verifies this stack; it does not publish images or deploy.

**This is not a production deployment configuration.** The existing [production adoption gate](docs/PRODUCTION_ADOPTION.md) remains in force.

## Configuration

The application reads these environment variables:

| Variable | Purpose | Default/production behavior |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | Required explicitly |
| `DB_USERNAME` | PostgreSQL username | Required explicitly |
| `DB_PASSWORD` | PostgreSQL password | Required; blank rejected |
| `APP_USERNAME` | HTTP Basic username | Required explicitly |
| `APP_PASSWORD` | HTTP Basic password | Required; blank rejected |

Copy `.env.example` as a reference for the expected values. Spring Boot does not load `.env` files automatically, so provide the variables through your shell, IDE run configuration, or deployment service. The `.env` file is ignored by Git.

### Environment modes

| Mode | Schema owner | Credential/seed policy |
| --- | --- | --- |
| No active profile, or `prod` | Existing schema only; Hibernate validates; Flyway disabled | All five settings above required; no demo seeding |
| `dev` | Flyway V1/validation on a personal development database | Local URL/user and explicitly labeled local-only API defaults; set DB_PASSWORD; seed off |
| `dev` plus `app.demo.seed=true` | Same as dev | Fictional records only if empty; repeated startup does not duplicate them |
| `migrated` (optionally with `prod`) | Flyway/validation | Explicit credentials and `app.migrations.approved=true` required outside dev/test; use only after adoption approval |
| `postgres-integration` (test resources only) | Disposable PostgreSQL/Flyway/validation | Container bindings/test credentials, seed off |
| `test` (test resources only) | Fast H2/create-drop; Flyway disabled | Existing fast fixture credentials |

`prod` mixed with dev/test profiles is rejected. Without a production profile, production-safe defaults still apply. Command-line/external/environment properties can override packaged settings; operators must review the effective values and active profiles. Required checks occur before datasource beans initialize and error messages identify keys, not credential values. The approval flag records deployment intent; it is not a substitute for human authorization. Spring Boot does not automatically load `.env` files.

## Running Locally

1. Install Java 21 and make sure PostgreSQL is running.
2. Create an empty personal `workorder` development database and a PostgreSQL user that can manage its schema. Do not use the existing deployed database.
3. Set `DB_PASSWORD`, `APP_USERNAME`, and `APP_PASSWORD`. Set the other variables too if your database does not use the documented defaults.
4. Start the application:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows, run `.\mvnw.cmd "-Dspring-boot.run.profiles=dev" spring-boot:run`. Flyway creates the fresh development schema; Hibernate validates it. To opt into fictional data, explicitly supply `app.demo.seed=true` through Spring configuration (for example `-Dspring-boot.run.arguments="--app.demo.seed=true"`). Never activate dev or test profiles on production. Existing unmanaged development databases also require a separately reviewed adoption plan or a new disposable database.

## Example Requests

Public list request:

```bash
curl http://localhost:8080/api/work-orders
```

Live public list request:

```bash
curl https://api.georgeizraelian.com/api/work-orders
```

Authenticated creation:

```bash
curl -u username:password \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Replace failed network switch",
    "description": "Connectivity is intermittent.",
    "priority": "HIGH",
    "requesterEmail": "operations@example.com",
    "dueDate": "2026-09-15"
  }' \
  http://localhost:8080/api/work-orders
```

Status update:

```bash
curl -u username:password \
  -X PATCH \
  -H "Content-Type: application/json" \
  -d '{"status":"IN_PROGRESS"}' \
  http://localhost:8080/api/work-orders/1/status
```

## Swagger

Local: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

The OpenAPI JSON is available at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs).

## Health

Local: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Only the Actuator health endpoint is exposed over HTTP.

## Live Demo

- Swagger UI: [https://api.georgeizraelian.com/swagger-ui/index.html](https://api.georgeizraelian.com/swagger-ui/index.html)
- Public API: [https://api.georgeizraelian.com/api/work-orders](https://api.georgeizraelian.com/api/work-orders)
- Health: [https://api.georgeizraelian.com/actuator/health](https://api.georgeizraelian.com/actuator/health)

## Deployment

The public portfolio demo runs on a DigitalOcean server using this deployment path:

```text
Internet
  |
  v
Nginx / HTTPS
  |
  v
Spring Boot
127.0.0.1:8080
  |
  v
PostgreSQL
localhost:5432
```

Ubuntu 24.04 hosts the Spring Boot JAR, and systemd manages the Java process. Nginx provides the public HTTPS reverse proxy while Spring Boot listens only on localhost. PostgreSQL also runs locally on the server and is not exposed publicly. Application and database credentials are supplied through environment variables and are not stored in Git.

## License

This project is available under the MIT License.

## M4B local workflow

See [M4B design, safeguards and verification](docs/M4B_LOCAL_WORKFLOW.md). Historical M4B checkpoint: writes were blocked before M4C added the isolated session mode.

## M4C secure local browser workflow

See [M4C verification and local setup](docs/M4C_VERIFICATION.md) and [security design](docs/M4C_SECURITY_DESIGN.md). The explicit local-browser profile/build supports session login, CSRF-protected writes and logout; production/default behavior remains isolated.

## M5 full-stack verification and presentation

Independently developed enterprise application engineering demonstration: Angular → HTTP/API → Spring Security → Spring Boot → JPA → PostgreSQL. Separate from commercial experience. The frontend now adds automated real Chromium acceptance against a fresh local Compose stack, including validation, create/status, reload/session restoration, SQL persistence, real inactivity expiry and logout. Existing 45 Maven tests include Testcontainers PostgreSQL/schema lifecycle and security isolation. GitHub Actions verifies; no authenticated public deployment is claimed.

- [Architecture, orchestration and CI](docs/ARCHITECTURE.md)
- [Security design summary and trade-offs](docs/SECURITY_DESIGN_SUMMARY.md)
- [Browser verification, screenshots and safe reproduction](https://github.com/gizraelian/angular-support-dashboard/blob/main/docs/M5_E2E_VERIFICATION.md)
- [Engineering case study and interview walkthrough](https://github.com/gizraelian/angular-support-dashboard/blob/main/docs/PORTFOLIO_CASE_STUDY.md)

The frontend `pnpm e2e` command builds a uniquely tagged image from this repository's verified JAR, creates its own development database with ephemeral credentials and preserves all volumes at shutdown. Run `.\mvnw.cmd clean verify` first and set M5_BACKEND_REPO in the frontend shell when checkouts are not siblings. Default/production Basic mode remains separate from explicit dev + local-browser sessions. The production Flyway adoption procedure and new backend deployment remain separately gated.
