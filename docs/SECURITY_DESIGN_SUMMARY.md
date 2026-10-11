# Security design summary

This demonstration retains M4C controls. It is not production-grade multiuser identity management. The default Basic API mode and opt-in local session mode use separate Spring Security chains.

## Decisions and threat model

Browser HTTP Basic was rejected for the development write workflow: browser-managed credentials and challenges do not provide the application's explicit session/logout UX, and automatically attached credentials do not remove CSRF risk. The existing Basic mode is preserved for its existing non-browser API-client contract. Session-based authentication was chosen for an explicit login, server invalidation on logout, inactivity expiration and cookie-only restoration after reload.

| Threat | Control | Remaining limitation |
| --- | --- | --- |
| Cross-site forged writes/login/logout | Cookie/header CSRF checks, Strict cookies, literal Host/Origin allowlist, no CORS exception | XSS can act as the user; CSRF is not an XSS defense |
| Session fixation/replay | ID rotation on login; invalidation and cookie removal on logout | In-memory session store; restart loses sessions |
| Credential/session disclosure | Password immediately cleared, no application browser storage, HttpOnly session cookie, no secret output or trace/video capture | Password-manager/OS behavior outside application control |
| Expired/anonymous write | Server 401; UI disables writes after expiry/logout; no mutation retry | Ambiguous network failure needs manual reload before resubmission |
| Development mode reaching production | Exclusive dev/test + local-browser profiles, explicit enablement and database/cookie/address checks; release compile-time gate/provider omission | Operators must still review effective config and artifacts |
| Unintended data exposure | Only fictional data in local tests/screenshots; unpublished database | Public GET DTO includes requesterEmail; real customer records must not be introduced |

The session cookie M4CSESSION is HttpOnly, host-only, SameSite=Strict, session-lifetime and scoped to /api. The CSRF cookie M4C-XSRF is readable by Angular on / and paired with X-M4C-XSRF on unsafe requests. Spring's SPA handler preserves deferred-token/BREACH handling; tokens refresh after login/logout. No CSRF values or session IDs are recorded as evidence.

Local HTTP explicitly sets Secure=false only inside the guarded loopback development arrangement. HTTPS requires Secure=true and TLS enablement. Cookies are isolated by host/path, not port; trust other services using this local host. No public authenticated deployment is authorized or claimed.

## M5 verification layers

Backend tests cover default/profile isolation, cookie/transport guards, login/session fixation, missing/invalid CSRF, origin/Host/Basic rejection, anonymous writes, logout and session-invalidated contract. The real HTTP harness verifies actual servlet inactivity expiry and cookie replay rejection. Playwright exercises the browser UI against real PostgreSQL, including actual expiry, no retry and independent SQL confirmation.

Release checks verify false local-build gate, empty production providers and absent local write transport/credential patterns. Browser release checks verify no local password/login/create/status controls. Trace/video/automatic failure screenshot capture is disabled; curated screenshots mask username/password inputs. Test credentials are random ephemeral development fixtures, not operator credentials. A safe reporter omits network bodies/cookies and redacts fixture credentials in assertion errors; the harness scrubs automatic textual error-context artifacts.

Production services/databases were not queried for M5 testing. The public portfolio homepage was read solely for presentation recommendations; hosting/repository metadata was read to evaluate push safety. Production Flyway adoption, backend deployment and public identity remain separate approval gates.

## Observed M5 checkpoint

2026-10-11: 45 Maven tests passed with no failures/errors/skips. Original default Basic Compose harness passed startup, credential guards, contracts and database persistence across restart/recreation. Real session harness passed CSRF negatives, fixation/rotation, cross-origin/Basic rejection, logout replay rejection and actual inactivity expiry. Frontend verification passed 75 unit tests, 2 real Chromium E2E tests, production build/artifact scan and actual same-origin release browser checks. Sanitized evidence and 11 reviewed PNGs live in frontend docs. No production database/service, migration or deployment was used to obtain these results.
