# M4B authentication design review and local workflow

M4B uses the explicitly authorized fallback: typed writes and tested frontend forms, with browser authentication blocked. This milestone does not claim an Angular authenticated create-to-persistence walkthrough. No application authentication architecture, authorization policy, migrations, production adoption gate, hosting or production infrastructure changed.

## Security decision

The existing SecurityConfig permits work-order GETs, requires authentication for writes, uses HTTP Basic and STATELESS sessions, and disables CSRF. The old comment incorrectly implied CSRF tokens cannot protect Basic clients; only that comment was corrected. Browser HTTP Basic caches can supply ambient credentials regardless of stateless server sessions. Keeping a JavaScript password only in memory cannot guarantee that all browser credential caches are cleared on logout, navigation or reload. `withCredentials: false` is not an assurance against same-origin cached Basic credentials. CORS, localhost binding, JSON content types and Angular's proxy do not establish a complete CSRF/credential-lifecycle boundary.

Reference: [Spring Security CSRF guidance](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), including its stateless browser applications and Basic authentication discussion. The selected fallback avoids relying on browser-specific Basic-cache behavior or an unreviewed security change.

Angular's development server binds 127.0.0.1:4200 and forwards /api/work-orders to http://127.0.0.1:8080. Compose publishes only loopback API ports; PostgreSQL has no published host port. Public builds contain no proxy configuration. Current Netlify routing sends unmatched paths to index.html and provides no backend API route. No browser request targets DigitalOcean.

There is no credential-entry form, Basic header construction, persistence in localStorage/sessionStorage, cookies for authentication, credential-bearing URL, or browser sign-in attempt. Browser credential lifecycle tests therefore do not apply: the application never accepts credentials. Pre-existing browser Basic caches are not cleared or managed by this application; the UI makes no logout claim.

WorkOrderService.createWorkOrder and updateStatus fail before any HTTP request unless local preview safeguards pass and an approved write transport is explicitly injected. Public/release mode blocks even an accidentally supplied transport. No application provider exists. WorkOrderHttpWriteContract is an unregistered contract adapter used only with HttpTestingController, without real network access or credentials. No runtime flag, environment file or build configuration enables writes. Local form previews require BOTH Angular development mode and the exact origin http://127.0.0.1:4200; production mode rejects even that origin. Opening /work-orders/new on a public build shows the blocker without a form. Adding a transport provider requires a new security review; it is not a supported toggle.

## Frontend workflow and limitations

The reactive create form uses title required/non-whitespace/max 120, description max 2000, required priority enum, required email/max 254, and optional HTML date input. Angular email validation is deliberately conservative; backend Bean Validation remains authoritative. Server validationErrors are mapped by field and rendered as text, preserving entered values. Editing clears stale server field errors. Backend malformed values and non-DTO errors receive safe feedback.

The status control uses the real status enum. Pending submissions suppress duplicate clicks. No create or PATCH pipeline retries requests. 401 and 403 are distinguished; ambiguous transport/5xx failures tell the user to reload before a manual resubmission. A successful mocked create navigates to details for a new GET; successful mocked status updates trigger a separate GET. Refresh failure remains distinct from write success. Pending subscriptions are canceled on destruction, and status observation is canceled on route changes. Canceling observation does not undo an already accepted server write.

Tickets retain separate domain types and fictional in-memory behavior. Existing work-order list/filter/page/detail read behavior and read-only retry policy remain intact. The browser preview does not create rows. A future approved transport must supply authorization/CSRF and preserve the existing no-write-retry rule.

## Smallest safe next step

Before enabling browser writes, separately approve a narrowly scoped local-development server-side CSRF design for browser authentication, with explicit release/profile safeguards and a tested credential lifecycle. Review Basic challenges/cache behavior, origin enforcement and direct-API/proxy access together. If reliable memory-only credential cleanup remains a requirement, choose an authentication mechanism without browser Basic caching. OIDC/BFF/session implementation is a separate decision, not part of M4B. Do not simply install the contract adapter as a provider or add a shared password to a bundle.

## Verification and walkthrough evidence

Backend: Maven verify passed 30 tests, including real PostgreSQL Testcontainers cases; four new controller tests cover incorrect Basic credentials, unauthorized PATCH, null-status field errors and successful status responses. Frontend: all 60 Angular tests passed; production build passed with 280.73 kB initial raw bundle. Added tests exercise valid/invalid creates, field errors, 401/403, status changes, failed refresh, manual failure recovery, duplicate pending submissions, cancellation, release/host gating, blocked default transport and no write retries after network/400/401/403/5xx responses. Backend currently has no role-based forbidden-write rule: its real unauthenticated/invalid-credential response is 401; frontend 403 handling is tested with mocked responses rather than inventing a backend policy.

The shell's system Node v20.11.0 fails the Angular CLI gate. Use the already installed bundled Node v24.19.0; no dependency/runtime installation was performed:

```powershell
$m4bNode = 'C:\Users\abana\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
& $m4bNode node_modules/@angular/cli/bin/ng.js test --watch=false
& $m4bNode node_modules/@angular/cli/bin/ng.js build
& $m4bNode node_modules/@angular/cli/bin/ng.js serve --port 4200
```

Browser inspection at /work-orders/new confirmed the local-only indication, initially disabled invalid submission, and the explicit blocker after entering a fictional valid title/email and clicking Check submission (blocked). No browser persistence is claimed. Screenshot: `M4B_BROWSER_BLOCKER.png` in the frontend docs directory.

The backend's `scripts/verify_compose.py --evidence` creates a fresh isolated project using the existing Compose definition and random ephemeral local test credentials. It uses only loopback HTTP, ignores system proxies, refuses redirects, never retries POST/PATCH, verifies create -> retrieve -> update -> GET plus direct PostgreSQL row values, then verifies the same values after restart and full container recreation. It verifies 401 unauthorized create/PATCH and 400 backend field errors. The project name/volume are unique; pre-existing volumes are rejected; only this disposable project is removed. Existing ignored development config/volumes remain intact. Sanitized evidence is written to the backend `docs/M4B_LOCAL_API_EVIDENCE.json`; credentials, Authorization headers and resolved Compose configuration are excluded. Without --evidence CI does not write documentation.

## Production readiness and M5 estimate

Existing public reads expose requesterEmail; real private/customer data requires an explicit access policy. HTTPS, CSRF-safe browser authentication, credential lifetime, scoped authorization, public API routing/CORS, production secret handling and the separate migration adoption gate remain unresolved. Existing ID precision and timezone-free timestamp limits remain unchanged. This is fictional personal-development evidence, not certification for production.

M5 scope has not been defined. For a focused next milestone approving and implementing safe local browser authentication, estimate 2-4 engineering days including review, release safeguards, adversarial CSRF tests and a real browser walkthrough. A production OIDC/BFF rollout would require separate scoping and authorization and is excluded from that estimate.

Antigravity: zero CLI invocations, zero delegated tasks and zero reported Antigravity usage. The blocked wrapper was neither probed nor bypassed; no approved compliant delegation mechanism was available. No token-savings estimate is claimed. Stop after M4B; no frontend deployment or production migration.

## Publication checks and files

Fetched remote heads matched the supplied milestone baseline (0 ahead / 0 behind). Full diffs were reviewed. GitHub hooks are empty for both personal repositories; backend deployment records are empty. Workflows only test/build and run isolated local verification. The official Netlify API currently reports frontend repo_url, repo_path, repo_branch and provider all null, published deploy 6a9ee86155a9fc78573ababa, and no backend Git deployment connection. No hosting settings were changed. Commit/push requires all final checks to pass and unchanged remote heads; GitHub Actions results are available on the eventual commit.

Changed files in this repository:

- `README.md`
- `docs/M4B_LOCAL_API_EVIDENCE.json`
- `docs/M4B_LOCAL_WORKFLOW.md`
- `scripts/verify_compose.py`
- `src/main/java/com/georgeizraelian/workorder/config/SecurityConfig.java`
- `src/test/java/com/georgeizraelian/workorder/controller/WorkOrderControllerTest.java`
