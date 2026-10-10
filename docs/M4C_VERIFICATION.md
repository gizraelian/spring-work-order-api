# M4C secure local browser workflow verification

## Result and implementation

M4C implements the authorized local session workflow. Spring Security form login/logout, server-memory sessions, fixation protection, cookie CSRF and a development-only controller replace the M4B browser blocker ONLY in the explicitly enabled local-browser configuration. Production/default Basic behavior remains in its separate chain; the local-auth namespace is denied outside the local-browser profile. Work-order REST semantics, ticket data, Flyway V1 and the production adoption gate were preserved. No production connection, migration, hosting setting, paid infrastructure or deployment was made.

Read [the security design](M4C_SECURITY_DESIGN.md) for profile guards, the SPA handler/BREACH/deferred-token behavior and local HTTP trade-offs.

## Local configuration

Use the existing ignored backend `.env.compose.local`, whose LOCAL_APP_USERNAME/PASSWORD and database credentials are explicitly supplied local-only values. No new password was invented or saved by M4C. Never send these values in chat, screenshots or shell history. Backend startup rejects missing explicit APP_USERNAME/APP_PASSWORD even if dev defaults exist. Development login credentials are entered only in the Angular login form; password controls are cleared immediately after submission and on logout. The single development user is not production multiuser identity management.

The isolated verification project is `workorder-m4c-20261010`. Existing projects and volumes were not changed. Its API binding is exactly 127.0.0.1:8080 and PostgreSQL is unpublished. Compose override uses `!override` and requires Compose 2.24.4 or newer (verified with 5.1.4). Build the artifact/image before starting:

```powershell
# Backend repository
.\mvnw.cmd clean verify
docker build -t spring-work-order-api:local .
$env:LOCAL_BROWSER_ENABLED = 'true'
docker compose --env-file .env.compose.local -p workorder-m4c-20261010 -f compose.yaml -f compose.local-browser.yaml up -d --no-build --wait --wait-timeout 180
# Frontend repository; use the already installed supported Node runtime
$m4cNode = 'C:\Users\abana\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
& $m4cNode node_modules/@angular/cli/bin/ng.js serve --configuration local-browser --port 4200
```

Open http://127.0.0.1:4200/work-orders/new. Generic development builds retain the blocker; only the explicit local-browser build registers the session write transport. Local HTTP uses an explicit allow-loopback-http exception: HttpOnly, host-only, SameSite=Strict session cookie M4CSESSION on /api, Secure=false. The separate readable M4C-XSRF cookie is on /. HTTPS must retain Secure=true and an enabled TLS configuration; no TLS-warning bypass or public exception was introduced. Cookie isolation is by host/path, not by port, so trust other local services on this host. Browser password-manager behavior is outside application control; no save-password prompt was accepted and the application stores no credentials in browser storage. No remember-me or persistent session cookie is used.

## Security and regression results

- Backend Maven verify: 45 tests passed, no failures or skips. Includes correct/incorrect login, real cookie/header CSRF checks, session creation/fixation, anonymous writes, authenticated writes, missing/invalid CSRF, unsafe methods and logout CSRF, cross-origin/Host/Basic rejection, logout invalidation, expired-session contract, profile/cookie/transport/database safeguards, production controller/chain absence and original Basic/PostgreSQL regression cases.
- Angular: 75 tests passed. Includes login/logout, reload recovery, immediate password clearing, duplicate-submission prevention, no browser storage reads, create/status/field errors, 401/403/CSRF feedback, expiry, refresh failure, no automatic mutation retries, accidental-provider release blocking and existing ticket/read tests.
- Production build passed, initial raw bundle 335.72 kB. `scripts/verify_release.mjs` confirms compile-time flag false, empty release provider list, local session transport omitted, and no test credential/private-key/token patterns in built assets. Actual ignored local credentials are also compared against candidate source and release files before publication.
- The existing `scripts/verify_compose.py` Basic harness passed unchanged runtime mode, including PostgreSQL persistence across restart and container recreation.
- New `scripts/verify_browser_session.py --running --evidence --expiry` verified actual servlet session cookies, no Basic challenge, session-ID rotation, CSRF rotation, anonymous 401, authenticated 403, cross-origin rejection, login/logout CSRF, backend field errors, create/status/GET/direct PostgreSQL, logout cookie removal and replay rejection of the session that was current immediately before logout. A real 1-minute Tomcat inactivity timeout rejected writes after 70 seconds with 401. The MockMvc expiry test validates the invalidated-session contract; the real timeout is verified separately, without claiming that MockMvc advances a servlet-container clock.
- Backend CI also runs the new harness with --expiry in a fresh randomly named isolated Compose project, after the original Basic harness has cleaned its project. CI credentials are random ephemeral fixtures; local operator credentials are never sent to CI.

Default inactivity timeout is 15 minutes. Verification supplied LOCAL_BROWSER_SESSION_TIMEOUT=1m without editing the ignored config. Tomcat timeout granularity is minutes; a requested sub-minute duration is not a promise of per-second expiration. There is no background session keep-alive in Angular.

## Real browser evidence

The actual in-app browser at the approved loopback origin completed:

1. Login using the existing ignored local credentials (password submitted once through the form, not compiled or retained).
2. Create form backend validation: a deliberate control-space title (U+001C) passes JavaScript's existing whitespace check but is rejected by backend Bean Validation. The UI displayed `title: must not be blank` and kept other entered values. The test did not remove client validators or mock the server response.
3. A valid create generated work order #5, title `M4C fictional network repair`, priority HIGH, requester `demo@example.com`, fictional description.
4. Angular retrieved #5, PATCH changed its status to IN_PROGRESS, then a full page reload restored the session and retrieved the saved values.
5. An independent PostgreSQL query returned `5|M4C fictional network repair|IN_PROGRESS|HIGH|demo@example.com`.
6. After 129 seconds without session requests, a browser attempt to change status to COMPLETED received 401. The UI showed the expired/invalidated session prompt and did not retry. PostgreSQL still contained IN_PROGRESS.
7. Re-login recovered access; logout invalidated the session, cleared credential controls, refreshed the anonymous CSRF cookie and disabled writes. The API harness independently confirmed server invalidation and replay rejection.

Sanitized screenshots in frontend docs: `M4C_BROWSER_VALIDATION.png`, `M4C_BROWSER_PERSISTED.png`, `M4C_BROWSER_EXPIRED.png`, `M4C_BROWSER_LOGOUT.png`. They contain fictional records and session-state messages, without passwords, CSRF values or session identifiers. The backend's `M4C_API_SECURITY_EVIDENCE.json` contains named assertions only; API-harness fixture IDs are separate from browser-created #5.

Actual browser positive writes and expiry are distinguished from direct HTTP negative cases: CSRF-negative/origin/replay requests were exercised with a real cookie jar against the same isolated backend by the harness, not by altering the application's browser state or inventing test UI controls.

## Repeatable verification and stop

```powershell
# Backend repository: original non-browser harness requires the default image/Compose mode
python scripts/verify_compose.py
# Session harness creates/cleans its own new project; port 8080 must be free:
python scripts/verify_browser_session.py --expiry
# Or test only the named existing local verification project:
python scripts/verify_browser_session.py --running --evidence --expiry
# Frontend repository
& $m4cNode node_modules/@angular/cli/bin/ng.js test --watch=false
& $m4cNode node_modules/@angular/cli/bin/ng.js build
& $m4cNode scripts/verify_release.mjs
# Stop only the retained fictional demo stack; preserve its volume for a future walkthrough:
docker compose --env-file .env.compose.local -p workorder-m4c-20261010 -f compose.yaml -f compose.local-browser.yaml stop
```

The --running option does not restart or delete the existing project. The default harness rejects pre-existing volumes and cleans only its own disposable project. It ignores system proxies, refuses redirects and does not retry writes. Project inspection never dumps resolved secrets.

## Publication, limits and next milestone

Publication requires reviewed diffs, clean secret/artifact checks, unchanged remote heads and verified no public deployment trigger. Repository workflows only verify; backend now additionally verifies isolated session security and frontend checks release artifacts. The official Netlify API reports repo_url, repo_path, repo_branch and provider all null for this frontend, no backend Git linkage, and unchanged published deploy 6a9ee86155a9fc78573ababa. Both repository hooks are empty and backend deployment records are empty. The current public Netlify deployment is unchanged. Commits and exact CI results are reported in the completion message and remain available on GitHub.

HTTPS cookie/profile guards were tested; the end-to-end demo used the explicitly reviewed local HTTP exception. A local TLS/browser arrangement was not implemented or claimed.

Remaining limits: local HTTP and a single explicitly configured development user; no production roles/rate limiting/identity management, no public browser writes, no production session durability, and public reads still expose requesterEmail under the existing fictional-data policy. Sessions expire on inactivity and backend restart. Ambiguous write failures require manual reload before resubmission; no idempotency keys were added. Login/logout mutations are never automatically retried. Canceling a subscription does not undo a server write. No deployment or production-adoption approval is implied.

Antigravity: 0 invocations, 0 models called, 0 results and 0 observed usage. The blocked wrapper was not probed, bypassed, disabled or weakened. No compliant approved delegation route was available. M5 presentation work may be proposed after M4C completion, but was not started. No Netlify/DigitalOcean deployment or production migration occurred.

Actual production-mode browser verification at the SAME loopback origin showed zero local password fields and zero create buttons. Screenshot: frontend docs/M4C_BROWSER_RELEASE.png. This supplements the accidental-provider interceptor test and artifact scan.

## Changed files

- `.github/workflows/test.yml`
- `README.md`
- `compose.local-browser.yaml`
- `docs/M4C_API_SECURITY_EVIDENCE.json`
- `docs/M4C_SECURITY_DESIGN.md`
- `docs/M4C_VERIFICATION.md`
- `scripts/verify_browser_session.py`
- `src/main/java/com/georgeizraelian/workorder/config/EnvironmentSafetyConfig.java`
- `src/main/java/com/georgeizraelian/workorder/config/LocalBrowserSecurityConfig.java`
- `src/main/java/com/georgeizraelian/workorder/config/SecurityConfig.java`
- `src/main/java/com/georgeizraelian/workorder/config/SpaCsrfTokenRequestHandler.java`
- `src/main/java/com/georgeizraelian/workorder/controller/LocalSessionController.java`
- `src/main/resources/application-local-browser.properties`
- `src/main/resources/application.properties`
- `src/test/java/com/georgeizraelian/workorder/config/EnvironmentSafetyConfigTest.java`
- `src/test/java/com/georgeizraelian/workorder/config/LocalBrowserSecurityTest.java`
- `src/test/java/com/georgeizraelian/workorder/config/ProductionBrowserIsolationTest.java`

Final local verification used the same new session harness that CI runs, with a fresh random Compose project and ephemeral credentials; its project was removed afterward. The named browser demo stack was stopped with its fictional PostgreSQL volume retained.
