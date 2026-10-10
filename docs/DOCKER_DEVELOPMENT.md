# Local Docker development

This Compose stack is personal development only. It uses the M2 `dev` profile, Flyway V1 and Hibernate validation against its own PostgreSQL volume. It does not adopt or connect to the live DigitalOcean database. [Production adoption remains separately gated](PRODUCTION_ADOPTION.md).

## Architecture and prerequisites

Java 21/Maven Wrapper package the application outside Docker; a single runtime Docker stage copies only the executable JAR. The Java 21 Temurin Ubuntu runtime is digest-pinned and includes curl (verified for the health check). The API runs as UID/GID 10001, with a read-only root filesystem and writable /tmp. PostgreSQL 16.10 Alpine stores data in a project-scoped named volume.

API → db:5432 over the project network. PostgreSQL publishes no host port. API publishes 127.0.0.1:8080 by default. Inside the API container SERVER_ADDRESS must be 0.0.0.0 so Docker forwarding works; this does not change the host's localhost-only binding. Compose waits for PostgreSQL's pg_isready health check; API health uses curl against /actuator/health. Readiness is not a guarantee of ongoing availability, and pg_isready alone does not validate the password; successful API startup verifies connectivity and migration/validation.

Requires Java 21, the Maven wrapper and Docker with Linux containers/Compose supporting `up --wait` (Compose v2.20+). Do not automatically install/repair WSL or Docker. Base image security updates require deliberate digest refresh/reverification; image tags/digests are not a vulnerability certification.

## Build and start (PowerShell, repository root)

```powershell
.\mvnw.cmd clean verify
Copy-Item .env.compose.example .env.compose.local
# Edit .env.compose.local manually with four personal local-only credentials.
docker compose --env-file .env.compose.local -p workorder-dev config --quiet
docker compose --env-file .env.compose.local -p workorder-dev build
docker compose --env-file .env.compose.local -p workorder-dev up -d --wait --wait-timeout 180
docker compose --env-file .env.compose.local -p workorder-dev ps
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod 'http://127.0.0.1:8080/api/work-orders?page=0&size=10'
```

The first command runs all 26 tests, including real PostgreSQL Testcontainers tests, and creates the executable artifact. On Linux/macOS use `./mvnw clean verify`. The Dockerfile deliberately requires target/work-order-api-0.0.1-SNAPSHOT.jar, so a missing artifact fails build rather than packaging an arbitrary JAR. Repackage/rebuild after code changes; Docker doesn't rebuild the Java source. The .dockerignore allows only that JAR and Docker build definitions.

Credentials are required Compose substitutions, supplied at runtime; none is built into the image or committed Compose file. Blank variables fail `config --quiet`/startup. Shell LOCAL_* variables override the env file, so review your shell state. Do not run `docker compose config` without `--quiet`, `config --environment` or broad Docker inspect commands when sharing output: resolved configuration can expose credentials. Environment variables remain inspectable by local Docker administrators; this is a development setup, not a production secret manager. Restrict access to your ignored env file. Values containing `$` should be single-quoted in the env file to avoid interpolation.

Spring does not load .env.compose.local directly; Compose maps LOCAL_* to the application's DB_*/APP_* settings. The DB address and dev profile are fixed in Compose; production DB_URL/APP_PASSWORD shell variables are not forwarded. M2's default/prod settings, migration approval gate, baseline prohibition and Hibernate validation are unchanged.

## Requests and persistence

Public reads need no credentials. For local writes, obtain credentials from your own ignored file/session; avoid putting passwords in shell history or shared logs. Example using a prompted PowerShell credential:

```powershell
$localCredential = Get-Credential
$body = @{title='Local work order';priority='HIGH';requesterEmail='demo@example.com'} | ConvertTo-Json
$order = Invoke-RestMethod http://127.0.0.1:8080/api/work-orders -Method Post -ContentType application/json -Body $body -Authentication Basic -Credential $localCredential -AllowUnencryptedAuthentication
docker compose --env-file .env.compose.local -p workorder-dev restart
docker compose --env-file .env.compose.local -p workorder-dev up -d --wait --wait-timeout 180
Invoke-RestMethod "http://127.0.0.1:8080/api/work-orders/$($order.id)"
```

This is intentionally local HTTP with fictional records, not production transport security. The named volume survives restart, stop and ordinary down. For stronger persistence verification, run ordinary down followed by up and retrieve the same ID; both containers are recreated while volume data remains. Demo seeding is false by default; explicitly set LOCAL_DEMO_SEED=true only if desired. Existing records prevent reseeding.

## Stop, cleanup and rebuild

```powershell
docker compose --env-file .env.compose.local -p workorder-dev stop
docker compose --env-file .env.compose.local -p workorder-dev start
# Remove this project's containers/network but preserve database:
docker compose --env-file .env.compose.local -p workorder-dev down
# DESTRUCTIVE: only when you intentionally want to discard THIS project's local database:
docker compose --env-file .env.compose.local -p workorder-dev down --volumes
```

Do not use system-wide prune or remove unrelated resources. Use a distinct project name for disposable verification; never reuse a project/volume containing data you need. Volumes are persistence, not backups. Do not delete an existing database just to accommodate migration failures.

## Troubleshooting

- Docker engine unavailable: check `docker info`; start Docker Desktop manually. Report WSL/engine instability instead of automatically repairing it.
- Port occupied: set LOCAL_API_PORT to an available local port and rerun up; keep 127.0.0.1 binding.
- Missing credentials: fill the four LOCAL_* settings; never switch to trust authentication or reuse production values.
- Changed PostgreSQL password/user: POSTGRES_* initialize an empty volume only; changing the env file does not change users in existing data. Restore the original local credentials or perform a separately planned local credential rotation. Do not reset valuable volumes casually.
- Migration refuses non-empty unmanaged schema: stop and follow the adoption review; never enable baseline-on-migrate to bypass it.
- Unhealthy API: inspect this project's `logs --tail=100 api` privately; verify DB health, migrations and validation. Health failure does not automatically restart a container.
- Permission/filesystem failures: retain non-root/read-only configuration; investigate writable /tmp and artifact ownership rather than running as root by default.
- Base image unavailable: report registry/network failure; deliberately review another supported Java 21 image/digest instead of silently using latest.

## CI and limits

Existing CI now runs Maven clean verify, builds the same image and boots an isolated Compose project with generated local test credentials. It checks HTTP health, public/authorized requests and persistence across container recreation, then removes only that CI project and its volume. It does not push images or deploy. GitHub-hosted execution still requires a later authorized commit/push and remote verification.

This container setup does not remove the documented Basic/CSRF/role limitations or authorize a production deployment. Public writes and real data remain outside this milestone.
