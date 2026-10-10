# M3 local verification — 2026-10-10

Implemented Dockerfile, .dockerignore, compose.yaml, blank .env.compose.example, scripts/verify_compose.py and Docker development runbook. Updated .gitignore, README and existing CI workflow. M1/M2 remained uncommitted; hashes confirmed their application code, configuration, migrations and tests were unchanged by M3. No branches, commits, pushes, production operations, Angular work or system repairs performed. No folders under C:\Coding\LLM were accessed.

## Results

| Check | Result |
| --- | --- |
| Docker/Compose prerequisites | Docker 29.6.1 Linux engine; Compose 5.1.4 |
| `.\mvnw.cmd clean verify` | PASS: 26 tests, zero failures/errors/skips; executable JAR packaged |
| `docker build -t spring-work-order-api:local .` | PASS; single runtime stage; 64.40 MB artifact context |
| `python scripts/verify_compose.py` | PASS; new isolated Compose project with generated local-only credentials |
| Credential failure checks | Each of four missing/blank LOCAL_* credentials rejected before startup |
| Fresh startup | Both containers healthy; Flyway V1 history correct; no fictional seed records |
| Runtime safety | API UID/GID 10001; API published only on 127.0.0.1; PostgreSQL has no host port; curl present in actual API container |
| REST | Health UP, unauthorized POST 401, invalid authorized POST 400, create 201/Location, status update 200, combined filtering/pagination |
| Restart persistence | Record/status, table OID and migration installation timestamp unchanged |
| Container recreation persistence | Same checks passed after ordinary down/up retaining named volume |
| Cleanup | Removed only project workorder-m3-8806fd5bd652 and its disposable volume; local built image retained |
| `git diff --check` | PASS |

Logs: target-m3-build.log, target/m3-image-build.log, target/m3-compose-verification.log (ignored local artifacts). Tests used actual PostgreSQL 16.10; H2 remains only in the existing fast suite. The verification script uses a unique project, refuses a pre-existing volume, uses loopback HTTP with proxy bypass, generates ephemeral passwords in memory, and suppresses resolved credential output.

## Image pins and architecture

Java runtime: eclipse-temurin:21-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c. PostgreSQL: postgres:16.10-alpine@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297. Refresh security patches deliberately; neither digest pinning nor these tests certifies absence of vulnerabilities.

Maven packages the tested JAR; Docker copies only that artifact. Non-root API uses a read-only root filesystem and tmpfs /tmp. Compose uses the local dev profile, fixed db service URL, required LOCAL_* credential substitutions, disabled seeding by default and project-scoped persistent PostgreSQL storage. PostgreSQL readiness precedes API creation; API uses curl against Actuator health. No multi-stage builder, published DB port, registry push or deployment machinery added.

## CI and remaining verification

CI now runs clean verify, image build and the same isolated Compose verification script. No credential secrets required in GitHub: verification generates ephemeral local credentials. Remote GitHub-hosted execution has not run because no commit/push was authorized. Confirm it after a later approved publication. Python 3, Linux Docker and Compose up --wait are required; standard ubuntu-latest runner support was not independently executed remotely.

## Compatibility/security limits

No M2 safeguard changed: default/prod Flyway remains disabled; Hibernate validation and production credential checks remain; migration approval/baseline/adoption gates unchanged. Compose is local dev only and must not be used to adopt the live database. Future production deployment still needs the separately approved inventory/backup/restore/baseline/rollback procedure in PRODUCTION_ADOPTION.md.

Development Basic over loopback HTTP, disabled CSRF and existing role limitations remain. Runtime environment values are visible to local Docker administrators; .env.compose.local is ignored, not a secret manager. Existing development-only fallback settings from M2 remain in the packaged application, but Compose supplies all four mandatory credentials and rejects absent/blank values; no generated verification passwords enter image/source/configuration. Use fictional records only. Volumes provide persistence, not backups.

## Antigravity and next task

Safety wrapper inspected and still blocks delegation before launch. Zero CLI/consultation invocations, zero successful delegated calls, no models used and no inference usage incurred; live quota not refreshed. No bypass attempted.

Recommended next task: M4's distinct Angular work-order HTTP module, preserving fictional ticket semantics and starting with typed contracts/list/detail and server pagination. Public write identity/security remains separately gated. Stop after M3 until approved.
