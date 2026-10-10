# Portfolio checkpoint and M4A live verification — 2026-10-10

## Review and publication safety

Both repositories are on main. GitHub API verifies owner gizraelian for spring-work-order-api and angular-support-dashboard. Fetch showed both local HEADs matched origin/main before the checkpoint (0 ahead / 0 behind); no unexpected remote commits. Full tracked diffs and new source/test/config files were reviewed. No Parsons repository was accessed or published.

The workflows are verification-only. Backend: Maven clean verify, local Docker image build and isolated Compose verification. Frontend: frozen pnpm install, tests and build. GitHub repository hooks are empty, and backend deployment records are empty. Backend README describes its existing DigitalOcean systemd/JAR deployment; no Git-push deployment mechanism was found in the reviewed repository, hooks or CI. No production server/database was contacted.

Current Netlify settings for angular-support-dashboard (site b9595119-6279-46b3-8334-02871ce219b5) were read from the official API using existing CLI authentication, with credentials and unrelated settings suppressed from output. build_settings repo_url, repo_path, repo_branch and provider are all null: main is not connected for automatic deployment. Published deploy 6a9ee86155a9fc78573ababa has deploy_source=api, context=production, build_id/branch/commit_ref null. No Netlify site was linked to the backend repository in the returned site inventory. A GitHub push will run CI but not the configured Netlify public deployment. Hosting settings were not changed. The published deploy ID was rechecked separately after any safe pushes.

Only the reviewed intended milestone source, tests, migration, Docker workflow, blank credential template and docs are staged. Git-ignored credentials, logs, Maven target, Angular dist/cache/node_modules and .netlify state are excluded. Candidate files were scanned for generated local credentials and high-confidence token/private-key patterns; no matches. The test-only fixture credentials and explicitly labeled dev-only defaults already in backend tests/configuration are not production secrets. Newly generated development credentials also do not occur in built Angular assets. Git cached whitespace checks are required before each commit.

One logical backend commit and one logical frontend commit are authorized. Final commit hashes, push results and remote CI outcomes are recorded in the final checkpoint response, rather than guessed in this pre-commit verification record.

## Current test/build results

| Check | Result |
| --- | --- |
| Backend .\mvnw.cmd clean verify | PASS: 26 tests, no failures/errors/skips; executable JAR packaged |
| Local Docker runtime image build | PASS: tested JAR, existing pinned Java 21 runtime |
| Angular full suite | PASS: 33 tests in 8 files |
| Angular production build | PASS: 280.42 kB initial raw bundle |
| Real Compose health | PASS: API and PostgreSQL healthy; Actuator UP |
| Anonymous proxy list/detail | PASS: actual HTTP requests through port 4200 to port 8080 |
| Server pagination | PASS: 16 rows; first/second pages 10/6, no overlapping IDs |
| Combined filters | PASS: OPEN + HIGH returns 13 matching real PostgreSQL rows |
| Empty query | PASS: CANCELLED + LOW returns empty content, totalElements 0 |
| Missing record | PASS: HTTP 404 with actual ApiError fields |
| API unavailable | PASS: stopping only this project's API produces proxy HTTP 502 and Angular alert/retry state |
| Fictional tickets | PASS: 12 in-memory records, queue/detail pages; work-order API availability independent |

Angular used the existing bundled Node v24.19.0; the shell's system Node v20.11.0 remains too old for this Angular version. No system tool, dependency or hosting upgrades were performed.

## Local isolation and fixtures

Used existing compose.yaml explicitly (-f compose.yaml) with project workorder-m4a-20261010 and a newly created .env.compose.local. Preflight refused existing personal configuration or project volumes; new credentials were stored only in that Git-ignored file, never disclosed or committed. LOCAL_* / COMPOSE_FILE / COMPOSE_PROFILES / COMPOSE_PROJECT_NAME shell overrides were removed from the subprocess environment. Database URL and dev profile remain fixed by Compose; no DigitalOcean configuration is used.

API publishes only 127.0.0.1:8080; PostgreSQL publishes no host port. Both container health checks passed. Four existing fictional development seed records were explicitly opted in. Twelve additional fictional pagination fixtures were inserted by container-local SQL only after checking current_database=workorder and exactly four initial seed rows in the newly isolated project. The same database then had 16 rows. No browser-authenticated writes were performed. Backend automated tests use their own disposable Testcontainers databases.

Real API and dev-proxy responses were compared for equality. Actual Spring VIA_DTO page metadata is size/number/totalElements/totalPages nested under page, with content alongside. Detail data equals the selected list record. Two pages have no overlapping IDs. Filters, empty and 404 behavior were checked over HTTP and in the browser. Browser verified the real list and detail titles, page two with six records, both enum filters, zero-result state, missing-ID message and unavailable state. Ticket queue/detail remained fictional. Temporary browser/dev-server sessions are closed after verification; this Compose project is stopped while its named volume and ignored config are retained for repeatable local development.

To resume locally (backend repository; only this isolated project):

```powershell
docker compose -f compose.yaml --env-file .env.compose.local -p workorder-m4a-20261010 up -d --no-build --wait --wait-timeout 180
```

Then start Angular with supported Node and its existing development proxy. Do not use this configuration against a production database. Do not disclose the ignored credential file or resolved docker compose config output.

## Remaining M4A limits and M4B recommendation

No blocking local read-integration defect was found. The development proxy is not bundled in production assets. The current Netlify /* fallback would handle /api requests as index.html; public API routing must be designed and separately approved before deployment. Backend reads remain public and expose requesterEmail; real private data needs an explicit access policy. Work-order timestamps are LocalDateTime without timezone, and IDs beyond JavaScript's safe integer range are rejected by the detail route; these are existing contract limits.

For M4B, prefer an individually authenticated browser session behind the same HTTPS origin, with Secure/HttpOnly session cookies, deliberate SameSite settings, CSRF protection for state-changing requests, and server-side authorization per user/role. A standard OIDC provider can establish individual identity; if a backend-for-frontend is used, keep service credentials solely on the server. Never bundle Basic credentials or persist tokens in browser localStorage as a shortcut. Review logout/expiry, unauthorized UI states, CORS/same-origin strategy and the public-read policy before implementing writes. Backend authentication, CSRF and public GET rules were not changed here.

Antigravity wrapper source was inspected read-only: it emits BLOCKED_SAFETY and exits before launch. Zero consultations/CLI calls, zero models and no inference usage or token-savings estimate. No safeguard bypass. No directory under C:\Coding\LLM was used.

Stopped after checkpoint/live verification. No M4B, public writes, production migrations, production deployment or hosting changes.
